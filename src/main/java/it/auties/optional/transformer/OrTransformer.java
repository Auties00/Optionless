package it.auties.optional.transformer;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import it.auties.optional.tree.Maker;

import java.util.Set;

import static com.sun.tools.javac.util.List.of;

public class OrTransformer extends FunctionalTransformer {
    public OrTransformer(TreeMaker treeMaker, Maker callMaker) {
        super(treeMaker, callMaker);
    }

    @Override
    public JCTree.JCStatement createMethodBody(String instruction, JCTree.JCMethodDecl enclosingMethod, JCTree.JCMethodInvocation invocation, JCTree.JCVariableDecl parameter, Symbol.ClassSymbol enclosingClass) {
        var orFunction = callMaker.createMethodFromLambda(enclosingClass, enclosingMethod, invocation.getArguments().head);
        var orFunctionExpression = treeMaker.App(treeMaker.Ident(orFunction.sym), callMaker.createIdentifiesFromParameters(of(parameter), orFunction.getParameters()));
        var checkCondition = callMaker.createObjectsCall("isNull", of(treeMaker.Ident(parameter.sym)));
        var conditional = treeMaker.Conditional(checkCondition, orFunctionExpression, callMaker.createNullType());
        return treeMaker.Return(conditional.setType(parameter.type))
                .setType(parameter.type);
    }

    @Override
    public String generatedMethodName() {
        return "otherwise";
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("or");
    }
}
