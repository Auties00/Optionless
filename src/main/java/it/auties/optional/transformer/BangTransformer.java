package it.auties.optional.transformer;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import it.auties.optional.tree.Maker;

import java.util.Set;

import static com.sun.tools.javac.util.List.nil;
import static com.sun.tools.javac.util.List.of;

public class BangTransformer extends FunctionalTransformer{
    public BangTransformer(TreeMaker treeMaker, Maker callMaker) {
        super(treeMaker, callMaker);
    }

    @Override
    public JCTree.JCStatement createMethodBody(String instruction, JCTree.JCMethodDecl enclosingMethod, JCTree.JCMethodInvocation invocation, JCTree.JCVariableDecl parameter, Symbol.ClassSymbol enclosingClass) {
        var checkCondition = callMaker.createObjectsCall("isNull", of(treeMaker.Ident(parameter.sym)));
        var ifTrue = createCheck(instruction, enclosingClass, enclosingMethod, invocation);
        var ifFalse = treeMaker.Return(treeMaker.Ident(parameter.sym));
        return treeMaker.If(checkCondition, ifTrue, ifFalse)
                .setType(parameter.type);
    }

    @Override
    public String generatedMethodName() {
        return "bang";
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("get", "getAsInt", "getAsLong", "getAsDouble", "orElseThrow");
    }

    private JCTree.JCStatement createCheck(String instruction, Symbol.ClassSymbol enclosingClass, JCTree.JCMethodDecl enclosingMethod, JCTree.JCMethodInvocation invocation){
        if (!instruction.equals("orElseThrow") || invocation.getArguments().isEmpty()) {
            return callMaker.createNoSuchElementException();
        }

        var exceptionThrower = callMaker.createMethodFromLambda(enclosingClass, enclosingMethod, invocation.getArguments().head);
        return treeMaker.Throw(treeMaker.App(treeMaker.Ident(exceptionThrower.sym), nil()));
    }
}
