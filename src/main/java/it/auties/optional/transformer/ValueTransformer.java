package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Maker;

import java.util.Objects;
import java.util.Set;

public class ValueTransformer extends OptionalTransformer{
    public ValueTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public JCTree.JCExpression transform() {
        var nonNull = Objects.equals(instruction, "isPresent");
        return isMemberReferenceScoped() ? maker.createDummyNullCheck(nonNull)
                : maker.createNullCheck(invocationCaller, nonNull);
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("isPresent", "isEmpty");
    }
}
