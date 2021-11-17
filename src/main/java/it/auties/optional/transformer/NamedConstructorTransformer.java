package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Maker;

import java.util.Set;

public class NamedConstructorTransformer extends OptionalTransformer{
    public NamedConstructorTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public JCTree.JCExpression transform() {
        return switch (instruction){
            case "empty" -> maker.createNullType();
            case "of" -> {
                var parameter = invocationArguments.head;
                if (parameter.type.isPrimitive()){
                    yield parameter;
                }

                yield maker.createNullAssert(parameter);
            }
            case "ofNullable" -> invocationArguments.head;
            default -> throw new IllegalStateException("NamedConstructorTransformer: %s is not a supported instruction".formatted(instruction));
        };
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("of", "ofNullable", "empty");
    }
}
