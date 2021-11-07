package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Maker;

import java.util.Objects;
import java.util.Set;

public class FunctionalElvisTransformer extends FunctionalTransformer{
    public FunctionalElvisTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("orElse", "orElseGet");
    }

    @Override
    public String name() {
        return "elvis";
    }

    @Override
    public JCTree.JCStatement body() {
        var valueSymbol = createIdentifierForParameter(0);
        var checkCondition = callMaker.createNullCheck(valueSymbol, false);
        var conditional = callMaker.trees().Conditional(checkCondition, Objects.requireNonNullElse(generatedInvocations.head, createIdentifierForParameter(1)), valueSymbol);
        return callMaker.trees().Return(conditional.setType(valueSymbol.type))
                .setType(valueSymbol.type);
    }
}
