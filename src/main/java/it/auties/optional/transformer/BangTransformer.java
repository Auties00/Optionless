package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Maker;

import java.util.Set;

public class BangTransformer extends FunctionalTransformer{
    public BangTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public JCTree.JCStatement body() {
        var value = createIdentifierForParameter(0);
        var checkCondition = maker.createNullCheck(value, false);
        return maker.trees()
                .If(checkCondition, throwException(), returnIdentifier(value));
    }

    private JCTree.JCStatement throwException(){
        if (generatedInvocations.isEmpty()) {
            return maker.createThrowNoSuchElementException();
        }

        return maker.trees().Throw(generatedInvocations.head)
                .setType(generatedInvocations.head.type);
    }

    private JCTree.JCStatement returnIdentifier(JCTree.JCIdent value) {
        return maker.trees().Return(value)
                .setType(value.type);
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("get", "getAsInt", "getAsLong", "getAsDouble", "orElseThrow");
    }
}
