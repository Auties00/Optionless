package it.auties.optional.transformer;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import it.auties.optional.tree.Maker;

import java.util.Set;

import static com.sun.tools.javac.util.List.of;

public class ConditionalTransformer extends FunctionalTransformer{
    public ConditionalTransformer(TreeMaker treeMaker, Maker callMaker) {
        super(treeMaker, callMaker);
    }

    @Override
    public JCTree.JCStatement createMethodBody(String instruction, JCTree.JCMethodDecl enclosingMethod, JCTree.JCMethodInvocation invocation, JCTree.JCVariableDecl parameter, Symbol.ClassSymbol enclosingClass) {
        var ifTrue = callMaker.createMethodFromLambda(enclosingClass, enclosingMethod, invocation.getArguments().head);
        var ifFalse = createElseCondition(enclosingClass, enclosingMethod, invocation, parameter);
        var ifTrueStatement = treeMaker.Exec(treeMaker.App(treeMaker.Ident(ifTrue.sym), callMaker.createIdentifiesFromParameters(of(parameter), ifTrue.getParameters())));;
        var checkCondition = callMaker.createObjectsCall("nonNull", of(treeMaker.Ident(parameter.sym)));
        return treeMaker.If(checkCondition, ifTrueStatement, ifFalse);
    }

    @Override
    public String generatedMethodName() {
        return "conditional";
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("ifPresent", "ifPresentOrElse");
    }

    private JCTree.JCStatement createElseCondition(Symbol.ClassSymbol enclosingClass, JCTree.JCMethodDecl enclosingMethod, JCTree.JCMethodInvocation invocation, JCTree.JCVariableDecl parameter) {
        if (invocation.getArguments().size() != 2) {
            return null;
        }

        var method = callMaker.createMethodFromLambda(enclosingClass, enclosingMethod, invocation.getArguments().last());
        return treeMaker.Exec(treeMaker.App(treeMaker.Ident(method.sym), callMaker.createIdentifiesFromParameters(of(parameter), method.getParameters())));
    }
}
