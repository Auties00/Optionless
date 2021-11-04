package it.auties.optional;

import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeMaker;

public class LambdaParameterCopier extends TreeCopier<Void> {
    public LambdaParameterCopier(TreeMaker M) {
        super(M);
    }

    @Override
    public JCTree visitVariable(VariableTree node, Void unused) {
        var tree = (JCTree.JCVariableDecl) node;
        var result = (JCTree.JCVariableDecl) super.visitVariable(node, unused);
        result.sym = tree.sym;
        result.sym.adr = 0;
        result.type = tree.type;
        return result;
    }
}
