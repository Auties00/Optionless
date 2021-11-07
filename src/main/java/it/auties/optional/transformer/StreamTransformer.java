package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Elements;
import it.auties.optional.tree.Maker;

import java.util.Set;

public class StreamTransformer extends OptionalTransformer{
    public StreamTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public JCTree transformTree(String instruction, JCTree.JCMethodInvocation invocation) {
        return callMaker.makeStream(Elements.getCallerExpression(invocation));
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("stream");
    }
}
