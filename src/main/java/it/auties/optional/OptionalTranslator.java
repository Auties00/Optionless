package it.auties.optional;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import lombok.AllArgsConstructor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import static com.sun.tools.javac.tree.TreeInfo.symbolFor;
import static com.sun.tools.javac.util.List.nil;
import static com.sun.tools.javac.util.List.of;

@AllArgsConstructor
public class OptionalTranslator extends TreeTranslator {
    private final TreeMaker maker;
    private final Types types;
    private final Names names;
    private ListBuffer<JCTree> generatedLambdas; // Can be improved by translating lambdas with visitLambda if the lambda is enclosed by the right tree
    private JCTree.JCClassDecl lastClass; // Can be improved by navigating the AST using the env
    private long functionalExpressionsCounter;
    private long functionalParametersCounter;
    private Type objectType;
    private Type objectsType;

    public OptionalTranslator(TreeMaker maker, Types types, JavacElements elements, Names names) {
        this(maker, types, names, new ListBuffer<>(),
                null, 0, 0,
                elements.getTypeElement(Object.class.getName()).asType(),
                elements.getTypeElement(Objects.class.getName()).asType()
        );
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl tree) {
        generatedLambdas.clear();
        this.lastClass = tree;
        super.visitClassDef(tree);
        tree.defs = tree.defs.appendList(generatedLambdas.toList());
    }

    @Override
    public void visitVarDef(JCTree.JCVariableDecl tree) {
        if(!isOptionalClass(tree.sym)){
            super.visitVarDef(tree);
            return;
        }

        var optionalType = findOptionalVariableType(tree);
        tree.sym.type = optionalType;
        tree.type = optionalType;
        tree.vartype = maker.Type(optionalType);
        super.visitVarDef(tree);
    }

    private Type findOptionalVariableType(JCTree.JCVariableDecl variable){
        var optionalType = getWrappedOptionalType(variable.type);
        if(optionalType != null){
            return types.removeWildcards(optionalType);
        }

        var initializer = variable.getInitializer();
        if(initializer == null){
            return objectType;
        }

        return types.removeWildcards(getWrappedOptionalType(initializer.type));
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        var returnType = findMethodReturnType(tree);
        if(!isOptionalClass(returnType.asElement())){
            super.visitMethodDef(tree);
            return;
        }

        var optionalType = getWrappedOptionalType(returnType);
        tree.type = optionalType;
        tree.restype = maker.Type(optionalType);
        if(tree.sym.type instanceof Type.MethodType methodType){
            methodType.restype = optionalType;
        }
        super.visitMethodDef(tree);
    }

    private Type findMethodReturnType(JCTree.JCMethodDecl method){
        return Optional.ofNullable(method.sym.getReturnType())
                .map(types::removeWildcards)
                .orElse(objectType);
    }

    private Type getWrappedOptionalType(Type returnType) {
        var head = returnType.getTypeArguments().head;
        if(head == null){
            return null;
        }

        var erased = types.erasure(head);
        if(hasOptionalTypeName(erased.asElement().getQualifiedName())){
            return getWrappedOptionalType(erased);
        }

        return Objects.requireNonNull(erased, "Empty optional type");
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
        var selected = symbolFor(tree);
        if (isOptionalNamedConstructor(selected)) {
            this.result = tree.getArguments().head;
            return;
        }

        if(isOwnedByOptional(selected)){
            desugarOptionalInvocation(tree, selected);
            return;
        }

        super.visitApply(tree);
    }

    private void desugarOptionalInvocation(JCTree.JCMethodInvocation tree, Symbol selected) {
        var invoked = selected.getSimpleName().toString();
        System.err.printf("De-sugaring %s%n", selected.getSimpleName());
        this.result = switch (invoked){
            case "get" -> desugarGetStatement(tree);
            case "orElse" -> desugarOrElseStatement(tree);
            case "isPresent" -> desugarIsPresentStatement(tree);
            case "isEmpty" -> desugarIsEmptyStatement(tree);
            case "ifPresent", "ifPresentOrElse" -> desugarIfPresentStatement(tree);
            case "map", "flatMap" -> desugarMapStatement(tree);
            default -> tree;
        };
    }

    private JCTree.JCMethodInvocation desugarOrElseStatement(JCTree.JCMethodInvocation invocation){
        var method = findObjectsMethod("requireNonNullElse");
        var arguments = invocation.getArguments().prepend(findInvocationTarget(invocation));
        var tree = maker.App(method, arguments);
        tree.type = objectsType;
        return tree;
    }

    private JCTree.JCMethodInvocation desugarGetStatement(JCTree.JCMethodInvocation invocation){
        var method = findObjectsMethod("requireNonNull");
        var tree = maker.App(method, of(findInvocationTarget(invocation)));
        tree.type = objectsType;
        return tree;
    }

    private JCTree.JCMethodInvocation desugarIsPresentStatement(JCTree.JCMethodInvocation invocation){
        var method = findObjectsMethod("nonNull");
        var tree = maker.App(method, of(findInvocationTarget(invocation)));
        tree.type = objectsType;
        return tree;
    }

    private JCTree.JCMethodInvocation desugarIsEmptyStatement(JCTree.JCMethodInvocation invocation){
        var method = findObjectsMethod("isNull");
        var tree = maker.App(method, of(findInvocationTarget(invocation)));
        tree.type = objectsType;
        return tree;
    }

    private JCTree.JCExpression desugarIfPresentStatement(JCTree.JCMethodInvocation invocation) {
        var target = findInvocationTarget(invocation);
        var params = of(maker.Param(names.fromString("inferred%s".formatted(functionalParametersCounter++)), getWrappedOptionalType(target.type), null));

        var ifTrue = desugarFunctionalExpression(invocation.getArguments().head);
        var ifFalse = invocation.getArguments().size() == 2 ? desugarFunctionalExpression(invocation.getArguments().last()) : null;
        var ifTrueStatement = maker.Exec(maker.App(maker.Ident(ifTrue.getName()).setType(lastClass.type), matchParamsToInvocation(params, ifTrue.getParameters())));
        var ifFalseStatement = createOrElseStatement(ifFalse, params);
        var checkCondition = maker.App(findObjectsMethod("isNull"), of(maker.Ident(params.head.getName()))).setType(lastClass.type);
        var check = maker.If(checkCondition, ifTrueStatement, ifFalseStatement);

        var method = maker.MethodDef(
                maker.Modifiers(Modifier.PRIVATE),
                names.fromString("ifPresent%s".formatted(functionalExpressionsCounter++)),
                maker.Type(types.erasure(symbolFor(invocation).type.getReturnType())),
                nil(),
                null,
                params,
                nil(),
                maker.Block(0L, of(check)),
                null
        );

        params.head.sym.owner = method.sym;
        generatedLambdas.add(method);
        return maker.App(maker.Ident(method.getName()).setType(lastClass.type), of(target)).setType(lastClass.type);
    }

    private JCTree.JCExpressionStatement createOrElseStatement(JCTree.JCMethodDecl ifFalse, List<JCTree.JCVariableDecl> params) {
        if (ifFalse == null){
            return null;
        }

        var identifier = maker.Ident(ifFalse.getName()).setType(lastClass.type);
        return maker.Exec(maker.App(identifier, matchParamsToInvocation(params, ifFalse.getParameters())));
    }

    private JCTree.JCExpression desugarMapStatement(JCTree.JCMethodInvocation invocation) {
        var target = findInvocationTarget(invocation);
        var params = of(maker.Param(names.fromString("inferred%s".formatted(functionalParametersCounter++)), getWrappedOptionalType(target.type), null));

        var mappingFunction = desugarFunctionalExpression(invocation.getArguments().head);
        var mappingFunctionExpression = maker.App(maker.Ident(mappingFunction.getName()).setType(lastClass.type), matchParamsToInvocation(params, mappingFunction.getParameters()));
        var checkCondition = maker.App(findObjectsMethod("isNull"), of(maker.Ident(params.head.getName()))).setType(lastClass.type);
        var returnStatement = maker.Return(maker.Conditional(checkCondition, maker.Literal(TypeTag.BOT, null), mappingFunctionExpression));

        var method = maker.MethodDef(
                maker.Modifiers(Modifier.PRIVATE),
                names.fromString("map%s".formatted(functionalExpressionsCounter++)),
                maker.Type(types.erasure(symbolFor(invocation).type.getReturnType())),
                nil(),
                null,
                params,
                nil(),
                maker.Block(0L, of(returnStatement)),
                null
        );

        params.head.sym.owner = method.sym;
        generatedLambdas.add(method);
        return maker.Assign(target, maker.App(maker.Ident(method.getName()).setType(lastClass.type), of(target)).setType(lastClass.type));
    }

    private List<JCTree.JCExpression> matchParamsToInvocation(List<JCTree.JCVariableDecl> methodParams, List<JCTree.JCVariableDecl> invocationParams){
        return methodParams.stream()
                .limit(invocationParams.size())
                .map(variable -> maker.Ident(variable).setType(lastClass.type))
                .collect(List.collector());
    }

    private JCTree.JCMethodDecl desugarFunctionalExpression(JCTree.JCExpression expression) {
        var name = names.fromString("generatedLambda%s".formatted(functionalExpressionsCounter++));
        return switch (expression){
            case JCTree.JCLambda lambda -> createMethodForExpression(name, lambda, extractLambdaParameters(lambda), extractLambdaBody(lambda));
            case JCTree.JCMemberReference reference -> {
                var parameters = findMemberReferenceParameters(reference);
                var methodCall = maker.App(maker.Select(reference.getQualifierExpression(), reference.getName()).setType(lastClass.type), parameters.map(maker::Ident));
                yield createMethodForExpression(name, reference, parameters, maker.Block(0L, of(maker.Exec(methodCall))));
            }

            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    private JCTree.JCBlock extractLambdaBody(JCTree.JCLambda lambda) {
        return switch (lambda.getBodyKind()){
            case STATEMENT -> (JCTree.JCBlock) lambda.getBody();
            case EXPRESSION -> {
                var expression = (JCTree.JCExpression) lambda.getBody();
                var symbol = symbolFor(expression);
                var parsedExpression = isVoid(symbol) ? maker.Exec(expression) : maker.Return(expression);
                yield maker.Block(0L, of(parsedExpression));
            }
        };
    }

    private List<JCTree.JCVariableDecl> extractLambdaParameters(JCTree.JCLambda lambda) {
        return lambda.getDescriptorType(types)
                .getTypeArguments()
                .stream()
                .peek(types::erasure)
                .map(type -> maker.Param(names.fromString("inferred%s".formatted(functionalParametersCounter++)), type, null))
                .collect(List.collector());
    }

    private JCTree.JCMethodDecl createMethodForExpression(Name name, JCTree.JCFunctionalExpression expression, List<JCTree.JCVariableDecl> parameters, JCTree.JCBlock body) {
        var result = maker.MethodDef(
                maker.Modifiers(Modifier.PRIVATE),
                name,
                maker.Type(findFunctionalExpressionType(expression)),
                nil(),
                parameters,
                nil(),
                body,
                null
        );
        result.getParameters().forEach(param -> param.sym.owner = result.sym);
        generatedLambdas.add(result);
        return result;
    }

    private List<JCTree.JCVariableDecl> findMemberReferenceParameters(JCTree.JCMemberReference reference) {
        return ((Symbol.MethodSymbol) reference.sym).getParameters()
                .map(parameter -> maker.Param(names.fromString("inferred%s".formatted(functionalParametersCounter++)), parameter.type, null));
    }

    private Type findFunctionalExpressionType(JCTree.JCFunctionalExpression lambda) {
        return types.erasure(lambda.getDescriptorType(types)).getReturnType();
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

    private JCTree.JCExpression findObjectsMethod(String methodName) {
        var members = Objects.requireNonNull(objectsType.asElement().members(), "The objects class has no members");
        var methodSymbol = Objects.requireNonNull(members.findFirst(names.fromString(methodName)), "Cannot find method: %s".formatted(methodName));
        return maker.Select(maker.Type(objectsType), methodSymbol);
    }

    private boolean isOptionalNamedConstructor(Symbol selected) {
        return isOptionalClass(selected) && hasOptionalNamedConstructorName(selected);
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

    private boolean hasOptionalNamedConstructorName(Symbol selected) {
        return Arrays.stream(Optional.class.getMethods())
                .filter(method -> hasFlag(method, Modifier.PUBLIC) && hasFlag(method, Modifier.STATIC))
                .anyMatch(method -> selected.getSimpleName().contentEquals(method.getName()));
    }

    private boolean hasOptionalTypeName(Name name){
        return name.contentEquals(Optional.class.getName());
    }

    private boolean hasFlag(Method method, int flag) {
        return (method.getModifiers() & flag) != 0;
    }

    private boolean isVoid(Symbol symbol) {
        return symbol != null && symbol.type.getTag() == TypeTag.VOID;
    }
}
