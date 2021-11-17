package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Maker;

import java.util.Set;

public class StreamTransformer extends OptionalTransformer{
    public StreamTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public JCTree.JCExpression transform() {
        return maker.makeStream(invocationCaller);
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("stream");
    }
}
