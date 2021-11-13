package it.auties.optional.transformer;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Maker;
import lombok.AllArgsConstructor;

import java.util.Set;

@AllArgsConstructor
public abstract class OptionalTransformer {
    protected final Maker maker;

    public JCTree transformTree(String instruction, JCTree.JCMethodInvocation invocation){
        throw new UnsupportedOperationException("%s has no transformer implementation".formatted(this.getClass().getName()));
    }

    public JCTree transformTree(String instruction, Symbol.ClassSymbol enclosingClass, JCTree.JCMethodDecl enclosingMethod, JCTree.JCMethodInvocation invocation){
        return transformTree(instruction, invocation);
    }

    public abstract Set<String> supportedInstructions();
}
