package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import it.auties.optional.tree.Elements;
import it.auties.optional.tree.Maker;

import java.util.Set;

public class StreamTransformer extends OptionalTransformer{
    public StreamTransformer(TreeMaker treeMaker, Maker callMaker) {
        super(treeMaker, callMaker);
    }

    @Override
    public JCTree transformTree(String instruction, JCTree.JCMethodInvocation invocation) {
        var target = Elements.getCallerExpression(invocation);
        return callMaker.createStreamCall("ofNullable", target);
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("stream");
    }
}
