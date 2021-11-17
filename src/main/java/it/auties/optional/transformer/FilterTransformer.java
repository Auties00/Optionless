package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Maker;

import java.util.Objects;
import java.util.Set;

public class FilterTransformer extends FunctionalTransformer{
    public FilterTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public JCTree.JCStatement body() {
        var parameter = createIdentifierForParameter(0);
        var filter = Objects.requireNonNullElseGet(generatedInvocations.head, () -> createIdentifierForParameter(1));
        var conditional = maker.trees()
                .Conditional(filter, parameter, maker.createNullType())
                .setType(parameter.type);
        return maker.trees()
                .Return(conditional)
                .setType(conditional.type);
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("filter");
    }
}
