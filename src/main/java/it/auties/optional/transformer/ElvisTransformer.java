package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Maker;

import java.util.Objects;
import java.util.Set;

public class ElvisTransformer extends FunctionalTransformer{
    public ElvisTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public JCTree.JCStatement body() {
        var valueSymbol = createIdentifierForParameter(0);
        var checkCondition = maker.createNullCheck(valueSymbol, false);
        var conditional = maker.trees().Conditional(checkCondition, Objects.requireNonNullElse(generatedInvocations.head, createIdentifierForParameter(1)), valueSymbol);
        return maker.trees().Return(conditional.setType(valueSymbol.type))
                .setType(valueSymbol.type);
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("orElse", "orElseGet");
    }
}
