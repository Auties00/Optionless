package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Maker;

import java.util.Set;

public class OrTransformer extends FunctionalTransformer {
    public OrTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public JCTree.JCStatement body() {
        var value = createIdentifierForParameter(0);
        var checkCondition = maker.createNullCheck(value, false);
        var conditional = maker.trees().Conditional(checkCondition, generatedInvocations.head, maker.createNullType());
        return maker.trees().Return(conditional.setType(value.type))
                .setType(value.type);
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("or");
    }
}
