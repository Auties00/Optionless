package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import it.auties.optional.tree.Maker;

import java.util.Set;

import static com.sun.tools.javac.util.List.of;

public class NamedConstructorTransformer extends OptionalTransformer{
    public NamedConstructorTransformer(TreeMaker treeMaker, Maker callMaker) {
        super(treeMaker, callMaker);
    }

    @Override
    public JCTree transformTree(String instruction, JCTree.JCMethodInvocation invocation) {
        return switch (instruction){
            case "empty" -> callMaker.createNullType();
            case "of" -> callMaker.createObjectsCall("requireNonNull", of(invocation.getArguments().head));
            case "ofNullable" -> invocation.getArguments().head;
            default -> throw new IllegalStateException("NamedConstructorTransformer: %s is not a supported instruction".formatted(instruction));
        };
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("of", "ofNullable", "empty");
    }
}
