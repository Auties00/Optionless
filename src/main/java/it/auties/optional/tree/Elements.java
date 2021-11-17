package it.auties.optional.tree;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Modifier;

@UtilityClass
public class Elements {
    public JCTree.JCExpression getCallerExpression(JCTree.JCMethodInvocation invocation){
        var skipped = TreeInfo.skipParens(invocation.getMethodSelect());
        if(skipped instanceof JCTree.JCFieldAccess fieldAccess){
            return fieldAccess.getExpression();
        }

        if(skipped instanceof JCTree.JCMemberReference memberReference) {
            return memberReference.getQualifierExpression();
        }

        throw new IllegalArgumentException("Unexpected tree %s".formatted(skipped));
    }

    public long createModifiers(JCTree.JCMethodDecl enclosingMethod){
        return (enclosingMethod.mods.flags & ~Modifier.PUBLIC  & ~Modifier.PRIVATE & ~Modifier.PROTECTED) | Modifier.PRIVATE;
    }

    public boolean isVoid(Symbol symbol) {
        return symbol != null
                && getReturnType(symbol.asType()).getTag() == TypeTag.VOID;
    }

    public Type getReturnType(Type type) {
        return type instanceof Type.MethodType methodType
                ? methodType.getReturnType() : type;
    }

    public Symbol.MethodSymbol getFunctionalInterfaceMethod(Symbol.TypeSymbol type){
        return (Symbol.MethodSymbol) type.members()
                .getSymbols(Elements::isFunctionalInterfaceMethod)
                .iterator()
                .next();
    }

    private boolean isFunctionalInterfaceMethod(Symbol symbol) {
        return symbol.kind == Kinds.Kind.MTH
                && noFlag(symbol, Flags.PRIVATE)
                && noFlag(symbol, Flags.SYNTHETIC)
                && noFlag(symbol, Flags.STATIC)
                && noFlag(symbol, Flags.DEFAULT);
    }

    private boolean noFlag(Symbol symbol, long flag) {
        return (symbol.flags() & flag) != flag;
    }
}
