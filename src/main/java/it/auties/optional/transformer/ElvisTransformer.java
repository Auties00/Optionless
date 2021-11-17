package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Elements;
import it.auties.optional.tree.Maker;

import java.util.Objects;
import java.util.Set;

public class ElvisTransformer extends FunctionalTransformer{
    public ElvisTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public JCTree.JCStatement body() {
        var checkCondition = maker.createNullCheck(createIdentifierForParameter(0), false);
        var elvis = Objects.requireNonNullElse(generatedInvocations.head, createIdentifierForParameter(1));
        var conditional = maker.trees()
                .Conditional(checkCondition, elvis, createIdentifierForParameter(0))
                .setType(Elements.getReturnType(elvis.type));
        return maker.trees()
                .Return(conditional)
                .setType(conditional.type);
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("orElse", "orElseGet");
    }
}
