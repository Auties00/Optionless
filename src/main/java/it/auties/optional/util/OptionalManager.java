package it.auties.optional.util;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.transformer.OptionalTransformer;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Accessors(fluent = true)
public record OptionalManager(Set<JCTree.JCMethodDecl> generatedLambdas,
                              Set<OptionalTransformer> transformers, AtomicInteger counter) {
    @Getter
    private static final OptionalManager instance = new OptionalManager(new HashSet<>(), new HashSet<>(), new AtomicInteger());

    public JCTree.JCMethodDecl addLambda(JCTree.JCMethodDecl methods){
        generatedLambdas.add(methods);
        return methods;
    }

    public OptionalManager addTransformer(OptionalTransformer translator){
        transformers.add(translator);
        return this;
    }

    public OptionalManager cleanLambdas(){
        generatedLambdas.clear();
        return this;
    }
}
