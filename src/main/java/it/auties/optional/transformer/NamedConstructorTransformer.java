package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Maker;

import java.util.Set;

public class NamedConstructorTransformer extends OptionalTransformer{
    public NamedConstructorTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public JCTree transformTree(String instruction, JCTree.JCMethodInvocation invocation) {
        return switch (instruction){
            case "empty" -> callMaker.createNullType();
            case "of" -> {
                var parameter = invocation.getArguments().head;
                if (parameter.type.isPrimitive()){
                    yield parameter;
                }

                yield callMaker.createNullAssert(parameter);
            }
            case "ofNullable" -> invocation.getArguments().head;
            default -> throw new IllegalStateException("NamedConstructorTransformer: %s is not a supported instruction".formatted(instruction));
        };
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("of", "ofNullable", "empty");
    }
}
