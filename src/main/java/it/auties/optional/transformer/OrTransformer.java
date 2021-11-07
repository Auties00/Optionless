package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Maker;

import java.util.Set;

public class OrTransformer extends FunctionalTransformer {
    public OrTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public String name() {
        return "otherwise";
    }

    @Override
    public JCTree.JCStatement body() {
        var checkCondition = callMaker.createNullCheck(createIdentifierForParameter(0), false);
        return callMaker.trees()
                .If(checkCondition, callMaker.trees().Exec(generatedInvocations.head), null);
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("or");
    }
}
