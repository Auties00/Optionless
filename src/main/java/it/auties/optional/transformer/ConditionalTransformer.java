package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Maker;

import java.util.Set;

public class ConditionalTransformer extends FunctionalTransformer{
    public ConditionalTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public JCTree.JCStatement body() {
        var checkCondition = maker.createNullCheck(createIdentifierForParameter(0), true);
        return maker.trees().If(checkCondition, maker.trees().Exec(generatedInvocations.head), orElse());
    }

    private JCTree.JCExpressionStatement orElse() {
        return generatedInvocations.size() >= 2 ? maker.trees().Exec(generatedInvocations.get(1))
                : null;
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("ifPresent", "ifPresentOrElse");
    }
}
