package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Maker;

import java.util.Set;

public class FilterTransformer extends FunctionalTransformer{

    public FilterTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public String name() {
        return "filter";
    }

    @Override
    public JCTree.JCStatement body() {
        var value = createIdentifierForParameter(0);
        var conditional = callMaker.trees().Conditional(generatedInvocations.head, value, callMaker.createNullType());
        return callMaker.trees().Return(conditional.setType(value.type))
                .setType(value.type);
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("filter");
    }
}
