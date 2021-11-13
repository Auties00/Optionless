package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Maker;

import java.util.Set;

public class FilterTransformer extends FunctionalTransformer{
    public FilterTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public JCTree.JCStatement body() {
        var value = createIdentifierForParameter(0);
        var conditional = maker.trees().Conditional(generatedInvocations.head, value, maker.createNullType());
        return maker.trees().Return(conditional.setType(value.type))
                .setType(value.type);
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("filter");
    }
}
