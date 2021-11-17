package it.auties.optional.transformer;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.List;
import it.auties.optional.tree.Elements;
import it.auties.optional.tree.FunctionalExpressionDesugarer;
import it.auties.optional.tree.Maker;

import java.util.stream.Stream;

import static com.sun.tools.javac.util.List.nil;
import static com.sun.tools.javac.util.List.of;

public abstract class FunctionalTransformer extends OptionalTransformer{
    protected JCTree.JCMethodDecl generatedMethod;
    protected List<JCTree.JCMethodDecl> generatedLambdas;
    protected List<JCTree.JCMethodInvocation> generatedInvocations;
    public FunctionalTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public JCTree.JCExpression transform() {
        this.generatedLambdas = generateFunctionalExpressions();
        this.generatedMethod = generateMethod();
        this.generatedInvocations = generateFunctionalCalls();
        generatedMethod.body = createBody();
        attributeGeneratedMethodType();
        return maker.createCallOnIdentifier(generatedMethod, createRootArguments());
    }

    private JCTree.JCMethodDecl generateMethod() {
        return maker.newMethod()
                .enclosingClass(enclosingClass)
                .modelMethod(enclosingMethod)
                .name(instruction)
                .parameters(createRootParameters())
                .toTree();
    }

    private void attributeGeneratedMethodType() {
        var methodType = (Type.MethodType) generatedMethod.type;
        var type = maker.eraseAndBox(generatedMethod.getBody().type, false);
        methodType.restype = type;
        generatedMethod.restype = maker.typeExpression(type);
    }

    private JCTree.JCBlock createBody() {
        var body = body();
        return (JCTree.JCBlock) maker.trees()
                .at(generatedMethod.pos())
                .Block(0L, of(body))
                .setType(body.type);
    }

    private List<JCTree.JCExpression> createRootArguments() {
        var explicitArguments = invocationArguments.stream()
                .filter(argument -> !(TreeInfo.skipParens(argument) instanceof JCTree.JCFunctionalExpression))
                .map(this::createRootArgument)
                .collect(List.collector());

        var deducedArguments = generatedMethod.getParameters()
                .stream()
                .skip(1)
                .limit(generatedMethod.getParameters().size() - 1 - explicitArguments.size())
                .map(parameter -> maker.identifier(parameter.sym))
                .collect(List.<JCTree.JCExpression>collector());

        return isMemberReferenceScoped() ? explicitArguments.appendList(deducedArguments)
                : explicitArguments.prepend(invocationCaller).appendList(deducedArguments);
    }

    private JCTree.JCExpression createRootArgument(JCTree.JCExpression parameter) {
        var type = Elements.getReturnType(parameter.type);
        if (!maker.types().isFunctionalInterface(type)) {
            return parameter;
        }

        var method = Elements.getFunctionalInterfaceMethod(type.asElement());
        return maker.trees().App(maker.trees().Select(parameter, method), isMemberReferenceScoped() ? nil() : of(invocationCaller));
    }

    private List<JCTree.JCMethodInvocation> generateFunctionalCalls() {
        return generatedLambdas.map(lambda -> {
            var arguments = generatedMethod.getParameters()
                    .stream()
                    .limit(lambda.getParameters().size())
                    .map(parameter -> maker.identifier(parameter.sym))
                    .collect(List.<JCTree.JCExpression>collector());
            return maker.createCallOnIdentifier(lambda, arguments);
        });
    }

    private List<JCTree.JCMethodDecl> generateFunctionalExpressions() {
        return invocationArguments.stream()
                .map(TreeInfo::skipParens)
                .filter(argument -> argument instanceof JCTree.JCFunctionalExpression)
                .map(argument -> maker.createMethodFromLambda(enclosingClass, enclosingMethod, argument))
                .collect(List.collector());
    }

    private List<JCTree.JCVariableDecl> createRootParameters(){
        return generatedLambdas.isEmpty() ? createRootParametersFromInvocation()
                : createRootParametersFromLambdas();
    }

    private List<JCTree.JCVariableDecl> createRootParametersFromInvocation() {
        return invocationArguments.stream()
                .map(expression -> maker.createInferredParameter(expression.type))
                .collect(List.collector())
                .prepend(maker.createInferredParameter(maker.boxed(invocationCallerType)));
    }

    private List<JCTree.JCVariableDecl> createRootParametersFromLambdas() {
        return generatedLambdas.stream()
                .flatMap(this::removeErasedTypeArguments)
                .collect(List.collector())
                .prepend(maker.createInferredParameter(maker.boxed(invocationCallerType)));
    }

    private Stream<JCTree.JCVariableDecl> removeErasedTypeArguments(JCTree.JCMethodDecl lambda) {
        var erased = ((FunctionalExpressionDesugarer.FunctionalExpressionType) lambda.type).erased();
        return lambda.getParameters()
                .stream()
                .skip(erased.getTypeArguments().size());
    }

    protected JCTree.JCIdent createIdentifierForParameter(int index){
        if(index >= generatedMethod.getParameters().size()){
            return null;
        }

        var symbol = generatedMethod.getParameters().get(index).sym;
        return maker.identifier(symbol);
    }

    protected abstract JCTree.JCStatement body();
}
