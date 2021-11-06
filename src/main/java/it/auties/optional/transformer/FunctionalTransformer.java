package it.auties.optional.transformer;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import it.auties.optional.tree.Elements;
import it.auties.optional.tree.Maker;

import static com.sun.tools.javac.util.List.of;

public abstract class FunctionalTransformer extends OptionalTransformer{
    public FunctionalTransformer(TreeMaker treeMaker, Maker callMaker) {
        super(treeMaker, callMaker);
    }

    @Override
    public JCTree transformTree(String instruction, Symbol.ClassSymbol enclosingClass, JCTree.JCMethodDecl enclosingMethod, JCTree.JCMethodInvocation invocation) {
        var target = Elements.getCallerExpression(invocation);
        var paramType = callMaker.unboxOptional(target.type);
        var parameter = callMaker.createInferredParameter(paramType);
        var method = callMaker.createMethod(enclosingClass, enclosingMethod, paramType, generatedMethodName(), of(parameter), createMethodBody(instruction, enclosingMethod, invocation, parameter, enclosingClass));
        return callMaker.createCallOnIdentifier(method, target);
    }

    public abstract JCTree.JCStatement createMethodBody(String instruction, JCTree.JCMethodDecl enclosingMethod, JCTree.JCMethodInvocation invocation, JCTree.JCVariableDecl parameter, Symbol.ClassSymbol enclosingClass);
    public abstract String generatedMethodName();
}
