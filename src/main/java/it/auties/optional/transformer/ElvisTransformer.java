package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Elements;
import it.auties.optional.tree.Maker;

import java.util.Objects;
import java.util.Set;

public class ElvisTransformer extends OptionalTransformer{
    public ElvisTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public JCTree transformTree(String instruction, JCTree.JCMethodInvocation invocation) {
        var target = Elements.getCallerExpression(invocation);
        return callMaker.createNullCheck(target, Objects.equals(instruction, "isPresent"));
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("isPresent", "isEmpty");
    }
}
