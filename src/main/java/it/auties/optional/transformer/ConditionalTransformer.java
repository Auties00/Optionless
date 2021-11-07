package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Maker;

import java.util.Set;

public class ConditionalTransformer extends FunctionalTransformer{

    public ConditionalTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public String name() {
        return "conditional";
    }

    @Override
    public JCTree.JCStatement body() {
        var checkCondition = callMaker.createNullCheck(createIdentifierForParameter(0), true);
        return callMaker.trees().If(checkCondition, callMaker.trees().Exec(generatedInvocations.head), orElse());
    }

    private JCTree.JCExpressionStatement orElse() {
        return generatedInvocations.size() >= 2 ? callMaker.trees().Exec(generatedInvocations.get(1))
                : null;
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("ifPresent", "ifPresentOrElse");
    }
}
