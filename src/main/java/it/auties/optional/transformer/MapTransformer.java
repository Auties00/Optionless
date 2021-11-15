package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Maker;

import java.util.Set;

public class MapTransformer extends FunctionalTransformer {
    public MapTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public JCTree.JCStatement body() {
        var checkCondition = maker.createNullCheck(createIdentifierForParameter(0), false);
        var conditional = maker.trees().Conditional(checkCondition, maker.createNullType(), generatedInvocations.head);
        return maker.trees().Return(conditional);
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("map", "flatMap");
    }
}
