package it.auties.optional.plugin;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import it.auties.optional.tree.Maker;
import it.auties.optional.util.OptionalManager;
import lombok.RequiredArgsConstructor;

import java.util.Objects;
import java.util.Optional;

import static com.sun.tools.javac.tree.TreeInfo.symbolFor;

@RequiredArgsConstructor
public class OptionalTranslator extends TreeTranslator {
    private final Maker maker;
    private final Types types;
    private final OptionalManager manager;
    private JCTree.JCClassDecl enclosingClass;
    private JCTree.JCMethodDecl enclosingMethod;

    @Override
    public void visitClassDef(JCTree.JCClassDecl tree) {
        this.enclosingClass = tree;
        super.visitClassDef(tree);
        tree.defs = tree.defs.appendList(List.from(manager.generatedLambdas()));
        manager.generatedLambdas().forEach(lambda -> tree.sym.members().enter(lambda.sym));
        manager.cleanLambdas();
    }

    @Override
    public void visitVarDef(JCTree.JCVariableDecl tree) {
        super.visitVarDef(tree);
        if(!isOptionalClass(tree.sym)){
            return;
        }

        var optionalType = findOptionalVariableType(tree);
        tree.sym.type = optionalType;
        tree.type = optionalType;
        tree.vartype = maker.createTypeExpression(optionalType);
    }

    private Type findOptionalVariableType(JCTree.JCVariableDecl variable){
        var optionalType = findInnerOptional(variable.type);
        if(optionalType != null){
            return types.removeWildcards(optionalType);
        }

        var initializer = Objects.requireNonNull(variable.getInitializer(), "findOptionalVariableType: null initializer");
        return types.removeWildcards(findInnerOptional(initializer.type));
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        this.enclosingMethod = tree;
        super.visitMethodDef(tree);
        var returnType = findMethodReturnType(tree);
        if(!isOptionalClass(returnType.asElement())){
            return;
        }

        var optionalType = findInnerOptional(returnType);
        tree.restype = maker.createTypeExpression(optionalType);
        ((Type.MethodType) tree.type).restype = optionalType;
        ((Type.MethodType) tree.sym.type).restype = optionalType;
    }

    private Type findMethodReturnType(JCTree.JCMethodDecl method){
        return Optional.of(method.sym.getReturnType())
                .map(types::removeWildcards)
                .orElseThrow();
    }

    private Type findInnerOptional(Type returnType) {
        var head = returnType.getTypeArguments().head;
        if(head == null){
            return types.boxedTypeOrType(types.erasure(returnType));
        }

        var erased = types.boxedTypeOrType(types.erasure(head));
        if(hasOptionalTypeName(erased.asElement().getQualifiedName())){
            return findInnerOptional(erased);
        }

        return Objects.requireNonNull(erased);
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
        super.visitApply(tree);
        var selected = symbolFor(tree);
        if (!isOwnedByOptional(selected)) {
            return;
        }

        desugarOptionalInvocation(tree, selected);
    }

    private void desugarOptionalInvocation(JCTree.JCMethodInvocation tree, Symbol selected) {
        var invoked = selected.getSimpleName().toString();
        this.result = manager.transformers()
                .stream()
                .filter(transformer -> transformer.supportedInstructions().contains(invoked))
                .findFirst()
                .map(transformer -> transformer.transformTree(invoked, enclosingClass.sym, enclosingMethod, tree))
                .orElseThrow(() -> new UnsupportedOperationException("No transformer could transform %s".formatted(invoked)));
    }

    private boolean isOwnedByOptional(Symbol selected) {
        return selected.getEnclosingElement() instanceof Symbol.ClassSymbol classSymbol
                && hasOptionalTypeName(classSymbol.getQualifiedName());
    }

    private boolean isOptionalClass(Symbol symbol){
        return switch (symbol){
            case null -> false;
            case Symbol.MethodSymbol method -> hasOptionalTypeName(method.getReturnType().asElement().getQualifiedName());
            case Symbol.VarSymbol variable -> hasOptionalTypeName(variable.asType().asElement().getQualifiedName());
            default -> hasOptionalTypeName(symbol.getQualifiedName()) || isOptionalClass(symbol.getEnclosingElement());
        };
    }

    private boolean hasOptionalTypeName(Name name){
        return name.contentEquals(Optional.class.getName());
    }
}
