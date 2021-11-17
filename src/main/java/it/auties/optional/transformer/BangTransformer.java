package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Elements;
import it.auties.optional.tree.Maker;

import java.util.Objects;
import java.util.Set;

public class BangTransformer extends FunctionalTransformer{
    public BangTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public JCTree.JCStatement body() {
        var value = createIdentifierForParameter(0);
        var checkCondition = maker.createNullCheck(value, false);
        var returner = returnParameter(value);
        return maker.trees()
                .If(checkCondition, throwException(), returner)
                .setType(returner.type);
    }

    private JCTree.JCStatement throwException(){
        if (generatedInvocations.isEmpty()) {
            return maker.createThrowNoSuchElementException();
        }

        var thrower = Objects.requireNonNullElse(generatedInvocations.head, createIdentifierForParameter(1));
        return maker.trees()
                .Throw(thrower)
                .setType(Elements.getReturnType(thrower.type));
    }

    private JCTree.JCStatement returnParameter(JCTree.JCIdent value) {
        return maker.trees()
                .Return(value)
                .setType(value.type);
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("get", "getAsInt", "getAsLong", "getAsDouble", "orElseThrow");
    }
}
