package it.auties.optional.transformer;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.List;
import it.auties.optional.tree.Elements;
import it.auties.optional.tree.Maker;
import it.auties.optional.util.FunctionalExpressionDesugarer;

import java.util.stream.Stream;

import static com.sun.tools.javac.util.List.of;

public abstract class FunctionalTransformer extends OptionalTransformer{
    protected String instruction;
    protected Symbol.ClassSymbol enclosingClass;
    protected JCTree.JCMethodDecl enclosingMethod;
    protected JCTree.JCMethodInvocation invocation;
    protected JCTree.JCMethodDecl generatedMethod;
    protected List<JCTree.JCMethodDecl> generatedLambdas;
    protected List<JCTree.JCMethodInvocation> generatedInvocations;
    public FunctionalTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public JCTree transformTree(String instruction, Symbol.ClassSymbol enclosingClass, JCTree.JCMethodDecl enclosingMethod, JCTree.JCMethodInvocation invocation) {
        this.instruction = instruction;
        this.enclosingClass = enclosingClass;
        this.enclosingMethod = enclosingMethod;
        this.invocation = invocation;

        this.generatedLambdas = generateFunctionalExpressions(enclosingClass, enclosingMethod, invocation);
        var callerExpression = Elements.getCallerExpression(invocation);
        var parameters = createRootParameters(callerExpression);

        var returnType = maker.unboxOptional(callerExpression.type);
        this.generatedMethod = maker.createMethod(enclosingClass, enclosingMethod, null, returnType, maker.uniqueName(instruction), parameters, null, true);
        this.generatedInvocations = generateFunctionalCalls();
        generatedMethod.body = maker.trees().Block(0L, of(body()));
        return maker.createCallOnIdentifier(generatedMethod, createRootArguments(callerExpression));
    }

    private List<JCTree.JCExpression> createRootArguments(JCTree.JCExpression callerExpression) {
        var explicitArguments = invocation.getArguments()
                .stream()
                .filter(argument -> !(TreeInfo.skipParens(argument) instanceof JCTree.JCFunctionalExpression))
                .collect(List.collector())
                .prepend(callerExpression);

        var deducedArguments = generatedMethod.getParameters()
                .stream()
                .skip(1)
                .limit(generatedMethod.getParameters().size() - explicitArguments.size())
                .map(parameter -> maker.identifier(parameter.sym))
                .collect(List.<JCTree.JCExpression>collector());

        return explicitArguments.appendList(deducedArguments);
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

    private List<JCTree.JCMethodDecl> generateFunctionalExpressions(Symbol.ClassSymbol enclosingClass, JCTree.JCMethodDecl enclosingMethod, JCTree.JCMethodInvocation invocation) {
        return invocation.getArguments()
                .stream()
                .map(TreeInfo::skipParens)
                .filter(argument -> argument instanceof JCTree.JCFunctionalExpression)
                .map(argument -> maker.createMethodFromLambda(enclosingClass, enclosingMethod, argument))
                .collect(List.collector());
    }

    private List<JCTree.JCVariableDecl> createRootParameters(JCTree.JCExpression caller){
        return generatedLambdas.isEmpty() ? createRootParametersFromInvocation(caller)
                : createRootParametersFromLambdas(caller);
    }

    private List<JCTree.JCVariableDecl> createRootParametersFromInvocation(JCTree.JCExpression caller) {
        return invocation.getArguments()
                .map(parameter -> maker.createInferredParameter(parameter.type))
                .prepend(maker.createInferredParameter(caller.type));
    }

    private List<JCTree.JCVariableDecl> createRootParametersFromLambdas(JCTree.JCExpression caller) {
        return generatedLambdas.stream()
                .flatMap(this::removeErasedTypeArguments)
                .collect(List.collector())
                .prepend(maker.createInferredParameter(caller.type));
    }

    private Stream<JCTree.JCVariableDecl> removeErasedTypeArguments(JCTree.JCMethodDecl lambda) {
        var erased = ((FunctionalExpressionDesugarer.FunctionalExpressionType) lambda.type).erased();
        return lambda.getParameters()
                .stream()
                .skip(erased.getTypeArguments().size());
    }

    protected JCTree.JCIdent createIdentifierForParameter(int index){
        var symbol = generatedMethod.getParameters().get(index).sym;
        return maker.identifier(symbol);
    }

    protected abstract JCTree.JCStatement body();
}
