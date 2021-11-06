package it.auties.optional.tree;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import it.auties.optional.util.LambdaParameterCopier;
import it.auties.optional.util.OptionalManager;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.sun.tools.javac.code.TypeTag.BOT;
import static com.sun.tools.javac.util.List.*;

public class Maker {
    private final TreeMaker maker;
    private final Names names;
    private final Symtab symtab;
    private final Types types;
    private final LambdaParameterCopier copier;
    private Type objectsType;
    private Type streamType;
    private Symbol.ClassSymbol noSuchElementSymbol;
    private int counter;

    public Maker(TreeMaker maker, Names names, Symtab symtab, Types types) {
        this.maker = maker;
        this.names = names;
        this.symtab = symtab;
        this.types = types;
        this.copier = new LambdaParameterCopier(maker);
    }

    public JCTree.JCMethodInvocation createCallOnIdentifier(JCTree.JCMethodDecl method, JCTree.JCExpression param) {
        return maker.App(maker.Ident(Objects.requireNonNull(method.sym, "Invalid symbol")), of(param));
    }

    public JCTree.JCExpression createTypeExpression(Type type){
        return maker.Type(type);
    }

    public JCTree.JCThrow createNoSuchElementException(){
        if(noSuchElementSymbol == null){
            this.noSuchElementSymbol = findBaseModuleClassSymbol(NoSuchElementException.class);
        }

        var ctor = noSuchElementSymbol.members().findFirst(names.init);
        return maker.Throw(maker.Create(ctor, of(maker.Literal("No value present"))));
    }

    public JCTree.JCExpression createStreamCall(String methodName, JCTree.JCExpression argument){
        if(streamType == null){
            this.streamType = findBaseModuleClassSymbol(Stream.class).asType();
        }

        return createMethodCall(streamType, methodName, of(argument));
    }

    public JCTree.JCExpression createObjectsCall(String methodName, List<JCTree.JCExpression> methodArguments) {
        if(objectsType == null){
            this.objectsType = findBaseModuleClassSymbol(Objects.class).asType();
        }

        return createMethodCall(objectsType, methodName, methodArguments);
    }

    public JCTree.JCMethodDecl createMethod(Symbol.ClassSymbol enclosingClass, JCTree.JCMethodDecl enclosingMethod, Type returnType, String name, List<JCTree.JCVariableDecl> parameters, JCTree.JCStatement body) {
        var methodName = names.fromString("%s%s".formatted(name, counter++));
        var methodType = new Type.MethodType(parameters.map(param -> param.type), returnType, nil(), enclosingClass);
        var methodSymbol = new Symbol.MethodSymbol(Elements.createModifiers(enclosingMethod), methodName, methodType, enclosingClass);
        methodSymbol.params = parameters.map(parameter -> parameter.sym);
        var method = maker.MethodDef(methodSymbol, maker.Block(0L, of(body)));
        parameters.forEach(param -> param.sym.owner = method.sym);
        OptionalManager.instance().addLambda(method);
        return method;
    }

    public JCTree.JCVariableDecl createInferredParameter(Type type) {
        var parameter = maker.Param(names.fromString("inferred%s".formatted(counter++)), type, null);
        parameter.sym.adr = 0;
        return parameter;
    }

    public Type unboxOptional(Type returnType) {
        var head = returnType.getTypeArguments().head;
        if(head == null){
            return types.boxedTypeOrType(types.erasure(returnType));
        }

        var erased = types.boxedTypeOrType(types.erasure(head));
        if(erased.asElement().getQualifiedName().contentEquals(Optional.class.getName())){
            return unboxOptional(erased);
        }

        return Objects.requireNonNull(erased);
    }

    public JCTree.JCLiteral createNullType() {
        return maker.Literal(BOT, null)
                .setType(symtab.botType);
    }

    private Symbol.ClassSymbol findBaseModuleClassSymbol(Class<?> clazz) {
        var baseModule = symtab.getModule(names.fromString("java.base"));
        var className = names.fromString(clazz.getName());
        return symtab.getClass(baseModule, className);
    }

    private JCTree.JCExpression createMethodCall(Type type, String methodName, List<JCTree.JCExpression> methodArguments) {
        var members = Objects.requireNonNull(type.asElement().members(), "The objects class has no members");
        var methodSymbol = members.findFirst(names.fromString(methodName), methodSymbolPredicate(methodArguments));
        var selected = maker.Select(createTypeExpression(type), methodSymbol);
        return maker.App(selected, methodArguments);
    }

    private Predicate<Symbol> methodSymbolPredicate(List<JCTree.JCExpression> methodArguments) {
        return symbol -> symbol.asType()
                .getParameterTypes()
                .stream()
                .allMatch(parameterType -> methodArguments.stream().allMatch(argumentType -> types.isAssignable(types.erasure(argumentType.type), types.erasure(parameterType))));
    }

    public JCTree.JCMethodDecl createMethodFromLambda(Symbol.ClassSymbol enclosingClass, JCTree.JCMethodDecl enclosingMethod, JCTree.JCExpression expression) {
        var name = names.fromString("generatedLambda%s".formatted(counter++));
        return switch (expression){
            case JCTree.JCLambda lambda -> createMethodForExpression(enclosingClass, enclosingMethod, name, lambda, extractLambdaParameters(lambda), extractLambdaBody(lambda));
            case JCTree.JCMemberReference reference -> {
                var parameters = findMemberReferenceParameters(reference);
                var methodCall = createSelectFromMemberReference(reference, parameters.map(maker::Ident));
                yield createMethodForExpression(enclosingClass, enclosingMethod, name, reference, parameters, maker.Block(0L, of(methodCall)));
            }

            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    private JCTree.JCStatement createSelectFromMemberReference(JCTree.JCMemberReference reference, List<JCTree.JCExpression> parameters) {
        if(reference.sym.getSimpleName().equals(names.init)){
            return maker.Return(maker.Create(reference.sym, parameters));
        }

        var result = maker.App(maker.Select(reference.getQualifierExpression(), reference.sym), parameters);
        return Elements.isVoid(reference.sym) ? maker.Exec(result) : maker.Return(result);
    }

    private JCTree.JCBlock extractLambdaBody(JCTree.JCLambda lambda) {
        return switch (lambda.getBodyKind()){
            case STATEMENT -> (JCTree.JCBlock) lambda.getBody();
            case EXPRESSION -> {
                var expression = (JCTree.JCExpression) lambda.getBody();
                var parsedExpression = Elements.isVoid(expression.type.asElement()) ? maker.Exec(expression) : maker.Return(expression);
                parsedExpression.setType(expression.type);
                expression.setType(expression.type);
                yield maker.Block(0L, of(parsedExpression));
            }
        };
    }

    private List<JCTree.JCVariableDecl> extractLambdaParameters(JCTree.JCLambda lambda) {
        var explicitParameters = lambda.params;
        if(!explicitParameters.isEmpty()){
            return copier.copy(explicitParameters);
        }

        return lambda.getDescriptorType(types)
                .getParameterTypes()
                .stream()
                .peek(types::erasure)
                .map(this::createInferredParameter)
                .collect(collector());
    }

    private JCTree.JCMethodDecl createMethodForExpression(Symbol.ClassSymbol enclosingClass, JCTree.JCMethodDecl enclosingMethod, Name name, JCTree.JCFunctionalExpression expression, List<JCTree.JCVariableDecl> parameters, JCTree.JCBlock body) {
        var methodReturnType = unboxOptional(expression.getDescriptorType(types).getReturnType());
        var methodType = new Type.MethodType(parameters.map(param -> param.type), methodReturnType, nil(), enclosingClass);
        var methodSymbol = new Symbol.MethodSymbol(Elements.createModifiers(enclosingMethod), name, methodType, enclosingClass);
        methodSymbol.params = parameters.map(param -> param.sym);
        var method = maker.MethodDef(methodSymbol, body);
        method.getParameters().forEach(param -> param.sym.owner = method.sym);
        return method;
    }

    private List<JCTree.JCVariableDecl> findMemberReferenceParameters(JCTree.JCMemberReference reference) {
        return ((Symbol.MethodSymbol) reference.sym).getParameters()
                .map(parameter -> createInferredParameter(parameter.type));
    }

    public List<JCTree.JCExpression> createIdentifiesFromParameters(List<JCTree.JCVariableDecl> methodParams, List<JCTree.JCVariableDecl> invocationParams){
        return methodParams.stream()
                .limit(invocationParams.size())
                .map(param -> maker.Ident(param.sym))
                .collect(collector());
    }
}
