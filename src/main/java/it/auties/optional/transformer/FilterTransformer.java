package it.auties.optional.transformer;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import it.auties.optional.tree.Elements;
import it.auties.optional.tree.Maker;

import java.util.Set;

import static com.sun.tools.javac.util.List.of;

public class FilterTransformer extends FunctionalTransformer{
    public FilterTransformer(TreeMaker treeMaker, Maker callMaker) {
        super(treeMaker, callMaker);
    }

    @Override
    public JCTree transformTree(String instruction, Symbol.ClassSymbol enclosingClass, JCTree.JCMethodDecl enclosingMethod, JCTree.JCMethodInvocation invocation) {
        var target = Elements.getCallerExpression(invocation);
        var paramType = callMaker.unboxOptional(target.type);
        var parameter = callMaker.createInferredParameter(paramType);

        var mappingFunction = callMaker.createMethodFromLambda(enclosingClass, enclosingMethod, invocation.getArguments().head);
        var mappingFunctionExpression = treeMaker.App(treeMaker.Ident(mappingFunction.sym), callMaker.createIdentifiesFromParameters(of(parameter), mappingFunction.getParameters()));
        var returnStatement = treeMaker.Return(treeMaker.Conditional(mappingFunctionExpression, treeMaker.Ident(parameter.sym), callMaker.createNullType()).setType(paramType)).setType(paramType);

        var method = callMaker.createMethod(enclosingClass, enclosingMethod, paramType, "filter", of(parameter), returnStatement);
        return callMaker.createCallOnIdentifier(method, target);
    }

    @Override
    public JCTree.JCStatement createMethodBody(String instruction, JCTree.JCMethodDecl enclosingMethod, JCTree.JCMethodInvocation invocation, JCTree.JCVariableDecl parameter, Symbol.ClassSymbol enclosingClass) {
        var filterFunction = callMaker.createMethodFromLambda(enclosingClass, enclosingMethod, invocation.getArguments().head);
        var filterCall = treeMaker.App(treeMaker.Ident(filterFunction.sym), callMaker.createIdentifiesFromParameters(of(parameter), filterFunction.getParameters()));
        var conditional = treeMaker.Conditional(filterCall, treeMaker.Ident(parameter.sym), callMaker.createNullType());
        return treeMaker.Return(conditional.setType(parameter.type))
                .setType(parameter.type);
    }

    @Override
    public String generatedMethodName() {
        return "filter";
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("filter");
    }
}
