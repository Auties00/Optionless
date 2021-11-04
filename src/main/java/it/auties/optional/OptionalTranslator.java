package it.auties.optional;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.sun.tools.javac.code.TypeTag.BOT;
import static com.sun.tools.javac.tree.TreeInfo.symbolFor;
import static com.sun.tools.javac.util.List.*;
import static javax.lang.model.element.Modifier.STATIC;

@RequiredArgsConstructor
public class OptionalTranslator extends TreeTranslator {
    private final TreeMaker maker;
    private final LambdaParameterCopier lambdaParameterCopier;
    private final Types types;
    private final Symtab symtab;
    private final Names names;
    private final Set<JCTree.JCMethodDecl> generatedLambdas; // Can be improved by translating lambdas with visitLambda if the lambda is enclosed by the right tree
    private JCTree.JCClassDecl enclosingClass; // Can be improved by navigating the AST using the env
    private JCTree.JCMethodDecl enclosingMethod; // Can be improved by navigating the AST using the env
    private long functionalExpressionsCounter;
    private long functionalParametersCounter;
    private Type objectType;
    private Type objectsType;
    private Type streamType;
    private Symbol.ClassSymbol noSuchElementSymbol;

    public OptionalTranslator(TreeMaker maker, Types types, Symtab symtab, Names names) {
        this(maker, new LambdaParameterCopier(maker), types, symtab, names, new HashSet<>());
        this.objectType = findBaseModuleClassSymbol(Object.class).asType();
        this.objectsType = findBaseModuleClassSymbol(Objects.class).asType();
        this.streamType = findBaseModuleClassSymbol(Stream.class).asType();
        this.noSuchElementSymbol = findBaseModuleClassSymbol(NoSuchElementException.class);
    }

    private Symbol.ClassSymbol findBaseModuleClassSymbol(Class<?> clazz) {
        var baseModule = symtab.getModule(names.fromString("java.base"));
        var className = names.fromString(clazz.getName());
        return symtab.getClass(baseModule, className);
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl tree) {
        generatedLambdas.clear();
        this.enclosingClass = tree;
        super.visitClassDef(tree);
        tree.defs = tree.defs.appendList(List.from(generatedLambdas));
        generatedLambdas.forEach(lambda -> tree.sym.members().enter(lambda.sym));
    }

    @Override
    public void visitVarDef(JCTree.JCVariableDecl tree) {
        super.visitVarDef(tree);
        if(!isOptionalClass(tree.sym)){
            return;
        }

        var optionalType = findOptionalVariableType(tree);
        tree.sym.type = optionalType;
        tree.type = optionalType;
        tree.vartype = maker.Type(optionalType);
    }

    private Type findOptionalVariableType(JCTree.JCVariableDecl variable){
        var optionalType = findInnerOptional(variable.type);
        if(optionalType != null){
            return types.removeWildcards(optionalType);
        }

        var initializer = variable.getInitializer();
        if(initializer == null){
            return objectType;
        }

        return types.removeWildcards(findInnerOptional(initializer.type));
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        this.enclosingMethod = tree;
        super.visitMethodDef(tree);
        var returnType = findMethodReturnType(tree);
        if(!isOptionalClass(returnType.asElement())){
            return;
        }

        var optionalType = findInnerOptional(returnType);
        tree.restype = maker.Type(optionalType);
        ((Type.MethodType) tree.type).restype = optionalType;
        ((Type.MethodType) tree.sym.type).restype = optionalType;
    }

    private Type findMethodReturnType(JCTree.JCMethodDecl method){
        return Optional.ofNullable(method.sym.getReturnType())
                .map(types::removeWildcards)
                .orElse(objectType);
    }

    private Type findInnerOptional(Type returnType) {
        var head = returnType.getTypeArguments().head;
        if(head == null){
            return types.boxedTypeOrType(types.erasure(returnType));
        }

        var erased = types.boxedTypeOrType(types.erasure(head));
        if(hasOptionalTypeName(erased.asElement().getQualifiedName())){
            return findInnerOptional(erased);
        }

        return Objects.requireNonNull(erased);
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
        super.visitApply(tree);
        var selected = symbolFor(tree);
        if (!isOwnedByOptional(selected)) {
            return;
        }

        desugarOptionalInvocation(tree, selected);
    }

    private void desugarOptionalInvocation(JCTree.JCMethodInvocation tree, Symbol selected) {
        var invoked = selected.getSimpleName().toString();
        this.result = switch (invoked){
            case "empty" -> createNullType();
            case "of" -> desugarOfNullableStatement(tree);
            case "ofNullable" -> tree.getArguments().head;
            case "orElse" -> desugarOrElseStatement(tree);
            case "orElseGet" -> desugarOrElseGetStatement(tree);
            case "get", "getAsInt", "getAsLong", "getAsDouble", "orElseThrow" -> tree.getArguments().isEmpty() ? desugarOrElseThrowStatement(tree) : desugarOrElseThrowExceptionStatement(tree);
            case "isPresent" -> desugarIsPresentStatement(tree);
            case "isEmpty" -> desugarIsEmptyStatement(tree);
            case "ifPresent", "ifPresentOrElse" -> desugarIfPresentStatement(tree);
            case "map", "flatMap"  -> desugarMapStatement(tree);
            case "or" -> desugarOrStatement(tree);
            case "filter" -> desugarFilterStatement(tree);
            case "stream" -> desugarStreamStatement(tree);
            default -> tree;
        };
    }

    private JCTree.JCMethodInvocation desugarOfNullableStatement(JCTree.JCMethodInvocation invocation){
        return createObjectsMethod("requireNonNull", of(invocation.getArguments().head))
                .setType(invocation.getArguments().head.type);
    }

    private JCTree.JCMethodInvocation desugarOrElseStatement(JCTree.JCMethodInvocation invocation){
        var arguments = invocation.getArguments().prepend(findInvocationTarget(invocation));
        return createObjectsMethod("requireNonNullElse", arguments);
    }

    private JCTree.JCMethodInvocation desugarOrElseGetStatement(JCTree.JCMethodInvocation invocation){
        var arguments = invocation.getArguments().prepend(findInvocationTarget(invocation));
        return createObjectsMethod("requireNonNullElseGet", arguments);
    }

    private JCTree.JCMethodInvocation desugarOrElseThrowStatement(JCTree.JCMethodInvocation invocation){
        var target = findInvocationTarget(invocation);
        var paramType = findInnerOptional(target.type);
        var parameter = createInferredParameter(paramType);

        var checkCondition = createObjectsMethod("isNull", parameter);
        var ctor = noSuchElementSymbol.members().findFirst(names.init);
        var check = maker.If(checkCondition, maker.Throw(maker.Create(ctor, of(maker.Literal("No value present")))), maker.Return(maker.Ident(parameter.sym)).setType(paramType));

        var methodName = names.fromString("orElseThrow%s".formatted(functionalExpressionsCounter++));
        var methodType = new Type.MethodType(of(paramType), paramType, nil(), enclosingClass.sym);
        var methodSymbol = new Symbol.MethodSymbol(createModifiers(), methodName, methodType, enclosingClass.sym);
        methodSymbol.params = of(parameter.sym);
        var method = maker.MethodDef(methodSymbol, maker.Block(0L, of(check)));
        parameter.sym.owner = method.sym;

        generatedLambdas.add(method);
        return createIdentifierMethod(method, target);
    }

    private JCTree.JCMethodInvocation desugarOrElseThrowExceptionStatement(JCTree.JCMethodInvocation invocation){
        var target = findInvocationTarget(invocation);
        var paramType = findInnerOptional(target.type);
        var parameter = createInferredParameter(paramType);

        var exceptionThrower = desugarFunctionalExpression(invocation.getArguments().head);
        var checkCondition = createObjectsMethod("isNull", parameter);
        var check = maker.If(checkCondition, maker.Throw(maker.App(maker.Ident(exceptionThrower.sym), nil())), maker.Return(maker.Ident(parameter.sym)).setType(paramType));

        var methodName = names.fromString("orElseThrow%s".formatted(functionalExpressionsCounter++));
        var methodType = new Type.MethodType(of(paramType), paramType, nil(), enclosingClass.sym);
        var methodSymbol = new Symbol.MethodSymbol(createModifiers(), methodName, methodType, enclosingClass.sym);
        methodSymbol.params = of(parameter.sym);
        var method = maker.MethodDef(methodSymbol, maker.Block(0L, of(check)));
        parameter.sym.owner = method.sym;

        generatedLambdas.add(method);
        return createIdentifierMethod(method, target);
    }

    private JCTree.JCMethodInvocation createObjectsMethod(String method, List<JCTree.JCExpression> methodArguments) {
        return maker.App(findObjectsMethod(method, methodArguments.map(argument -> argument.type)), methodArguments);
    }

    private JCTree.JCMethodInvocation createObjectsMethod(String method, JCTree.JCVariableDecl expression) {
        var identifier = maker.Ident(expression);
        return maker.App(findObjectsMethod(method, of(expression.type)), of(identifier));
    }

    private JCTree.JCMethodInvocation createIdentifierMethod(JCTree.JCMethodDecl method, JCTree.JCExpression param) {
        var identifier = maker.Ident(method.sym);
        return maker.App(identifier, of(param));
    }

    private JCTree.JCMethodInvocation desugarIsPresentStatement(JCTree.JCMethodInvocation invocation){
        return createObjectsMethod("nonNull", of(findInvocationTarget(invocation)));
    }

    private JCTree.JCMethodInvocation desugarIsEmptyStatement(JCTree.JCMethodInvocation invocation){
        return createObjectsMethod("isNull", of(findInvocationTarget(invocation)));
    }

    private JCTree.JCExpression desugarIfPresentStatement(JCTree.JCMethodInvocation invocation) {
        var target = findInvocationTarget(invocation);
        var paramType = findInnerOptional(target.type);
        var parameter = createInferredParameter(paramType);

        var ifTrue = desugarFunctionalExpression(invocation.getArguments().head);
        var ifFalse = invocation.getArguments().size() == 2 ? desugarFunctionalExpression(invocation.getArguments().last()) : null;
        var ifTrueStatement = maker.Exec(maker.App(maker.Ident(ifTrue.sym), matchParamsToInvocation(of(parameter), ifTrue.getParameters())));
        var ifFalseStatement = createOrElseStatement(ifFalse, of(parameter));
        var checkCondition = createObjectsMethod("nonNull", of(maker.Ident(parameter)));
        var check = maker.If(checkCondition, ifTrueStatement, ifFalseStatement);

        var methodName = names.fromString("ifPresent%s".formatted(functionalExpressionsCounter++));
        var methodType = new Type.MethodType(of(paramType), new Type.JCVoidType(), nil(), enclosingClass.sym);
        var methodSymbol = new Symbol.MethodSymbol(createModifiers(), methodName, methodType, enclosingClass.sym);
        methodSymbol.params = of(parameter.sym);
        var method = maker.MethodDef(methodSymbol, maker.Block(0L, of(check)));
        parameter.sym.owner = method.sym;

        generatedLambdas.add(method);
        return createIdentifierMethod(method, target);
    }

    private JCTree.JCExpressionStatement createOrElseStatement(JCTree.JCMethodDecl ifFalse, List<JCTree.JCVariableDecl> params) {
        return ifFalse != null ?
                maker.Exec(maker.App(maker.Ident(ifFalse.sym), matchParamsToInvocation(params, ifFalse.getParameters()))) : null;
    }

    private JCTree.JCExpression desugarMapStatement(JCTree.JCMethodInvocation invocation) {
        var target = findInvocationTarget(invocation);
        var paramType = findInnerOptional(target.type);
        var parameter = createInferredParameter(paramType);

        var mappingFunction = desugarFunctionalExpression(invocation.getArguments().head);
        var mappingFunctionExpression = maker.App(maker.Ident(mappingFunction.sym), matchParamsToInvocation(of(parameter), mappingFunction.getParameters()));
        var checkCondition = createObjectsMethod("isNull", parameter);
        var returnStatement = maker.Return(maker.Conditional(checkCondition, createNullType(), mappingFunctionExpression).setType(paramType)).setType(paramType);

        var methodName = names.fromString("map%s".formatted(functionalExpressionsCounter++));
        var methodType = new Type.MethodType(of(paramType), paramType, nil(), enclosingClass.sym);
        var methodSymbol = new Symbol.MethodSymbol(createModifiers(), methodName, methodType, enclosingClass.sym);
        methodSymbol.params = of(parameter.sym);
        var method = maker.MethodDef(methodSymbol, maker.Block(0L, of(returnStatement)));
        parameter.sym.owner = method.sym;

        generatedLambdas.add(method);
        return createIdentifierMethod(method, target);
    }

    private JCTree.JCExpression desugarOrStatement(JCTree.JCMethodInvocation invocation) {
        var target = findInvocationTarget(invocation);
        var paramType = findInnerOptional(target.type);
        var parameter = createInferredParameter(paramType);

        var orFunction = desugarFunctionalExpression(invocation.getArguments().head);
        var orFunctionExpression = maker.App(maker.Ident(orFunction.sym), matchParamsToInvocation(of(parameter), orFunction.getParameters()));
        var checkCondition = createObjectsMethod("isNull", parameter);
        var returnStatement = maker.Return(maker.Conditional(checkCondition, orFunctionExpression, createNullType()).setType(paramType)).setType(paramType);

        var methodName = names.fromString("map%s".formatted(functionalExpressionsCounter++));
        var methodType = new Type.MethodType(of(paramType), paramType, nil(), enclosingClass.sym);
        var methodSymbol = new Symbol.MethodSymbol(createModifiers(), methodName, methodType, enclosingClass.sym);
        methodSymbol.params = of(parameter.sym);
        var method = maker.MethodDef(methodSymbol, maker.Block(0L, of(returnStatement)));
        parameter.sym.owner = method.sym;

        generatedLambdas.add(method);
        return createIdentifierMethod(method, target);
    }

    private JCTree.JCExpression desugarFilterStatement(JCTree.JCMethodInvocation invocation) {
        var target = findInvocationTarget(invocation);
        var paramType = findInnerOptional(target.type);
        var parameter = createInferredParameter(paramType);

        var mappingFunction = desugarFunctionalExpression(invocation.getArguments().head);
        var mappingFunctionExpression = maker.App(maker.Ident(mappingFunction.sym), matchParamsToInvocation(of(parameter), mappingFunction.getParameters()));
        var returnStatement = maker.Return(maker.Conditional(mappingFunctionExpression, maker.Ident(parameter.sym), createNullType()).setType(paramType)).setType(paramType);

        var methodName = names.fromString("filter%s".formatted(functionalExpressionsCounter++));
        var methodType = new Type.MethodType(of(paramType), paramType, nil(), enclosingClass.sym);
        var methodSymbol = new Symbol.MethodSymbol(createModifiers(), methodName, methodType, enclosingClass.sym);
        methodSymbol.params = of(parameter.sym);
        var method = maker.MethodDef(methodSymbol, maker.Block(0L, of(returnStatement)));
        parameter.sym.owner = method.sym;

        generatedLambdas.add(method);
        return createIdentifierMethod(method, target);
    }

    private JCTree desugarStreamStatement(JCTree.JCMethodInvocation invocation) {
        var arguments = of(findInvocationTarget(invocation));
        var method = findMethod(streamType, "ofNullable", arguments.map(argument -> argument.type));
        return maker.App(method, arguments);
    }

    private JCTree.JCLiteral createNullType() {
        return maker.Literal(BOT, null).setType(symtab.botType);
    }

    private List<JCTree.JCExpression> matchParamsToInvocation(List<JCTree.JCVariableDecl> methodParams, List<JCTree.JCVariableDecl> invocationParams){
        return methodParams.stream()
                .limit(invocationParams.size())
                .map(param -> maker.Ident(param.sym))
                .collect(collector());
    }

    private JCTree.JCMethodDecl desugarFunctionalExpression(JCTree.JCExpression expression) {
        var name = names.fromString("generatedLambda%s".formatted(functionalExpressionsCounter++));
        return switch (expression){
            case JCTree.JCLambda lambda -> createMethodForExpression(name, lambda, extractLambdaParameters(lambda), extractLambdaBody(lambda));
            case JCTree.JCMemberReference reference -> {
                var parameters = findMemberReferenceParameters(reference);
                var methodCall = createSelectFromMemberReference(reference, parameters.map(maker::Ident));
                yield createMethodForExpression(name, reference, parameters, maker.Block(0L, of(methodCall)));
            }

            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    private JCTree.JCStatement createSelectFromMemberReference(JCTree.JCMemberReference reference, List<JCTree.JCExpression> parameters) {
        if(reference.sym.getSimpleName().equals(names.init)){
            return maker.Return(maker.Create(reference.sym, parameters));
        }

        var result = maker.App(maker.Select(reference.getQualifierExpression(), reference.sym), parameters);
        return isVoid(reference.sym) ? maker.Exec(result) : maker.Return(result);
    }

    private JCTree.JCBlock extractLambdaBody(JCTree.JCLambda lambda) {
        return switch (lambda.getBodyKind()){
            case STATEMENT -> (JCTree.JCBlock) lambda.getBody();
            case EXPRESSION -> {
                var expression = (JCTree.JCExpression) lambda.getBody();
                var parsedExpression = isVoid(expression.type.asElement()) ? maker.Exec(expression) : maker.Return(expression);
                parsedExpression.setType(expression.type);
                expression.setType(expression.type);
                yield maker.Block(0L, of(parsedExpression));
            }
        };
    }

    private List<JCTree.JCVariableDecl> extractLambdaParameters(JCTree.JCLambda lambda) {
        var explicitParameters = lambda.params;
        if(!explicitParameters.isEmpty()){
            return lambdaParameterCopier.copy(explicitParameters);
        }

        return lambda.getDescriptorType(types)
                .getParameterTypes()
                .stream()
                .peek(types::erasure)
                .map(this::createInferredParameter)
                .collect(collector());
    }

    private JCTree.JCVariableDecl createInferredParameter(Type type) {
        var parameter = maker.Param(names.fromString("inferred%s".formatted(functionalParametersCounter++)), type, null);
        parameter.sym.adr = 0;
        return parameter;
    }

    private JCTree.JCMethodDecl createMethodForExpression(Name name, JCTree.JCFunctionalExpression expression, List<JCTree.JCVariableDecl> parameters, JCTree.JCBlock body) {
        var methodReturnType = findFunctionalExpressionType(expression);
        var methodType = new Type.MethodType(parameters.map(param -> param.type), methodReturnType, nil(), enclosingClass.sym);
        var methodSymbol = new Symbol.MethodSymbol(createModifiers(), name, methodType, enclosingClass.sym);
        methodSymbol.params = parameters.map(param -> param.sym);
        var method = maker.MethodDef(methodSymbol, body);
        method.getParameters().forEach(param -> param.sym.owner = method.sym);
        generatedLambdas.add(method);
        return method;
    }

    private List<JCTree.JCVariableDecl> findMemberReferenceParameters(JCTree.JCMemberReference reference) {
        return ((Symbol.MethodSymbol) reference.sym).getParameters()
                .map(parameter -> createInferredParameter(parameter.type));
    }

    private Type findFunctionalExpressionType(JCTree.JCFunctionalExpression lambda) {
        return findInnerOptional(lambda.getDescriptorType(types).getReturnType());
    }

    private JCTree.JCExpression findInvocationTarget(JCTree.JCMethodInvocation invocation){
        var skipped = TreeInfo.skipParens(invocation.getMethodSelect());
        return switch (skipped){
            case JCTree.JCFieldAccess fieldAccess -> fieldAccess.getExpression();
            case JCTree.JCMemberReference memberReference -> memberReference.getQualifierExpression();
            case null -> throw new IllegalArgumentException("Unexpected tree: null");
            default -> throw new IllegalArgumentException("Unexpected tree: %s with type %s".formatted(skipped, skipped.getClass().getName()));
        };
    }

    private JCTree.JCExpression findObjectsMethod(String methodName, List<Type> methodArguments) {
        return findMethod(objectsType, methodName, methodArguments);
    }

    private JCTree.JCExpression findMethod(Type type, String methodName, List<Type> methodArguments) {
        var members = Objects.requireNonNull(type.asElement().members(), "The objects class has no members");
        var methodSymbol = members.findFirst(names.fromString(methodName), symbolMatcher(methodArguments));
        return maker.Select(maker.Type(type), methodSymbol);
    }

    private Predicate<Symbol> symbolMatcher(List<Type> methodArguments) {
        return symbol -> symbol.asType()
                .getParameterTypes()
                .stream()
                .allMatch(parameterType -> methodArguments.stream()
                        .anyMatch(argumentType -> types.isAssignable(types.erasure(argumentType), types.erasure(parameterType))));
    }

    private boolean isOwnedByOptional(Symbol selected) {
        return selected.getEnclosingElement() instanceof Symbol.ClassSymbol classSymbol
                && hasOptionalTypeName(classSymbol.getQualifiedName());
    }

    private boolean isOptionalClass(Symbol symbol){
        return switch (symbol){
            case null -> false;
            case Symbol.MethodSymbol method -> hasOptionalTypeName(method.getReturnType().asElement().getQualifiedName());
            case Symbol.VarSymbol variable -> hasOptionalTypeName(variable.asType().asElement().getQualifiedName());
            default -> hasOptionalTypeName(symbol.getQualifiedName()) || isOptionalClass(symbol.getEnclosingElement());
        };
    }

    private boolean hasOptionalTypeName(Name name){
        return name.contentEquals(Optional.class.getName());
    }

    private boolean isVoid(Symbol symbol) {
        if(symbol == null){
            return false;
        }

        return getSymbolReturnType(symbol).getTag() == TypeTag.VOID;
    }

    private Type getSymbolReturnType(Symbol symbol) {
        return symbol.type instanceof Type.MethodType methodType
                ? methodType.getReturnType() : symbol.type;
    }

    private int createModifiers(){
        if (!enclosingMethod.getModifiers().getFlags().contains(STATIC)) {
            return Modifier.PRIVATE;
        }

        return Modifier.PRIVATE | Modifier.STATIC;
    }
}
