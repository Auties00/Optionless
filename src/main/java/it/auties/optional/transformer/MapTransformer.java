package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Maker;

import java.util.Set;

public class MapTransformer extends FunctionalTransformer {
    public MapTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public String name() {
        return "mapper";
    }

    @Override
    public JCTree.JCStatement body() {
        var checkCondition = callMaker.createNullCheck(createIdentifierForParameter(0), false);
        var conditional = callMaker.trees().Conditional(checkCondition, callMaker.createNullType(), generatedInvocations.head);
        return callMaker.trees().Return(conditional.setType(generatedInvocations.head.type))
                .setType(generatedInvocations.head.type);
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("map", "flatMap");
    }
}
