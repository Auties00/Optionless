package it.auties.optional.transformer;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import it.auties.optional.tree.Elements;
import it.auties.optional.tree.Maker;

import java.util.Collection;
import java.util.Objects;

import static com.sun.tools.javac.tree.TreeInfo.symbolFor;
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

        var returnType = callMaker.unboxOptional(callerExpression.type);
        this.generatedMethod = callMaker.createMethod(enclosingClass, enclosingMethod, returnType, name(), parameters, null, true);
        this.generatedInvocations = generateFunctionalCalls();
        generatedMethod.body = callMaker.trees().Block(0L, of(body()));
        return callMaker.createCallOnIdentifier(generatedMethod, createRootArguments(callerExpression));
    }

    private List<JCTree.JCExpression> createRootArguments(JCTree.JCExpression callerExpression) {
        return invocation.getArguments()
                .stream()
                .filter(argument -> !(TreeInfo.skipParens(argument) instanceof JCTree.JCFunctionalExpression))
                .collect(List.collector())
                .prepend(callerExpression);
    }

    private List<JCTree.JCMethodInvocation> generateFunctionalCalls() {
        return generatedLambdas.map(lambda -> {
            var arguments = generatedMethod.getParameters()
                    .stream()
                    .limit(lambda.getParameters().size())
                    .map(parameter -> callMaker.identifier(parameter.sym))
                    .collect(List.<JCTree.JCExpression>collector());
            return callMaker.createCallOnIdentifier(lambda, arguments);
        });
    }

    private List<JCTree.JCMethodDecl> generateFunctionalExpressions(Symbol.ClassSymbol enclosingClass, JCTree.JCMethodDecl enclosingMethod, JCTree.JCMethodInvocation invocation) {
        return invocation.getArguments()
                .stream()
                .map(TreeInfo::skipParens)
                .filter(argument -> argument instanceof JCTree.JCFunctionalExpression)
                .map(argument -> callMaker.createMethodFromLambda(enclosingClass, enclosingMethod, argument))
                .collect(List.collector());
    }

    private List<JCTree.JCVariableDecl> createRootParameters(JCTree.JCExpression caller){
        return generatedLambdas.isEmpty() ? createRootParametersFromInvocation(caller)
                : createRootParametersFromLambdas();
    }

    private List<JCTree.JCVariableDecl> createRootParametersFromInvocation(JCTree.JCExpression caller) {
        return invocation.getArguments()
                .map(parameter -> callMaker.createInferredParameter(null, parameter.type))
                .prepend(callMaker.createInferredParameter(null, caller.type));
    }

    private List<JCTree.JCVariableDecl> createRootParametersFromLambdas() {
        return generatedLambdas.stream()
                .map(JCTree.JCMethodDecl::getParameters)
                .flatMap(Collection::stream)
                .collect(ListBuffer::new, this::distinctCollector, ListBuffer::addAll)
                .toList();
    }

    private void distinctCollector(ListBuffer<JCTree.JCVariableDecl> collection, JCTree.JCVariableDecl element){
        var symbol = symbolFor(element);
        if(collection.stream().anyMatch(result -> Objects.equals(symbolFor(result), symbol))){
            return;
        }

        collection.add(element);
    }

    protected JCTree.JCIdent createIdentifierForParameter(int index){
        Assert.checkNonNull(generatedMethod);
        Assert.check(index < generatedMethod.getParameters().size(), "Parameter overflow");
        return callMaker.identifier(generatedMethod.getParameters().get(index).sym);
    }

    public abstract String name();
    public abstract JCTree.JCStatement body();
}
