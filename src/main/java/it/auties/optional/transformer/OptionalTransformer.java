package it.auties.optional.transformer;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import it.auties.optional.tree.Maker;
import lombok.AllArgsConstructor;

import java.util.Set;

@AllArgsConstructor
public abstract class OptionalTransformer {
    protected final TreeMaker treeMaker;
    protected final Maker callMaker;

    public JCTree transformTree(String instruction, JCTree.JCMethodInvocation invocation){
        throw new UnsupportedOperationException(this.getClass().getName() + " has no transformer implementation");
    }

    public JCTree transformTree(String instruction, Symbol.ClassSymbol enclosingClass, JCTree.JCMethodDecl enclosingMethod, JCTree.JCMethodInvocation invocation){
        return transformTree(instruction, invocation);
    }

    public abstract Set<String> supportedInstructions();
}
