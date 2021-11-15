package it.auties.optional.tree;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Modifier;

import static javax.lang.model.element.Modifier.STATIC;

@UtilityClass
public class Elements {
    public JCTree.JCExpression getCallerExpression(JCTree.JCMethodInvocation invocation){
        var skipped = TreeInfo.skipParens(invocation.getMethodSelect());
        return switch (skipped){
            case JCTree.JCFieldAccess fieldAccess -> fieldAccess.getExpression();
            case JCTree.JCMemberReference memberReference -> memberReference.getQualifierExpression();
            case null -> throw new IllegalArgumentException("Unexpected tree: null");
            default -> throw new IllegalArgumentException("Unexpected tree: %s with type %s".formatted(skipped, skipped.getClass().getName()));
        };
    }

    public long createModifiers(JCTree.JCMethodDecl enclosingMethod){
        return (enclosingMethod.mods.flags & ~Modifier.PUBLIC  & ~Modifier.PRIVATE & ~Modifier.PROTECTED) | Modifier.PRIVATE;
    }

    public boolean isVoid(Symbol symbol) {
        return symbol != null
                && getSymbolReturnType(symbol).getTag() == TypeTag.VOID;
    }

    public Type getSymbolReturnType(Symbol symbol) {
        return symbol.type instanceof Type.MethodType methodType
                ? methodType.getReturnType() : symbol.type;
    }
}
