package it.auties.optional.transformer;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import it.auties.optional.tree.Maker;
import it.auties.optional.tree.Elements;

import java.util.Set;

public class ElvisTransformer extends OptionalTransformer{
    public ElvisTransformer(TreeMaker treeMaker, Maker callMaker) {
        super(treeMaker, callMaker);
    }

    @Override
    public JCTree transformTree(String instruction, JCTree.JCMethodInvocation invocation) {
        var target = Elements.getCallerExpression(invocation);
        var arguments = invocation.getArguments().prepend(target);
        return callMaker.createObjectsCall(inferObjectsInstruction(instruction), arguments);
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("orElse", "orElseGet", "isPresent", "isEmpty");
    }

    private String inferObjectsInstruction(String instruction){
        return switch (instruction){
            case "orElse" -> "requireNonNullElse";
            case "orElseGet" -> "requireNonNullElseGet";
            case "isPresent" -> "nonNull";
            case "isEmpty" -> "isNull";
            default -> throw new IllegalStateException("ElvisTransformer: %s is not a supported instruction".formatted(instruction));
        };
    }
}
