package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Maker;

import java.util.Set;

public class BangTransformer extends FunctionalTransformer{

    public BangTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public String name() {
        return "bang";
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("get", "getAsInt", "getAsLong", "getAsDouble", "orElseThrow");
    }

    @Override
    public JCTree.JCStatement body() {
        var value = createIdentifierForParameter(0);
        var checkCondition = callMaker.createNullCheck(value, false);
        return callMaker.trees().If(checkCondition, throwException(), returnIdentifier(value));
    }

    private JCTree.JCStatement throwException(){
        if (generatedInvocations.isEmpty()) {
            return callMaker.createNoSuchElementException();
        }

        return callMaker.trees().Throw(generatedInvocations.head)
                .setType(generatedInvocations.head.type);
    }

    private JCTree.JCStatement returnIdentifier(JCTree.JCIdent value) {
        return callMaker.trees().Return(value)
                .setType(value.type);
    }
}
