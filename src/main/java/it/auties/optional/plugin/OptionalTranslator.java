package it.auties.optional.plugin;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import it.auties.optional.transformer.OptionalTransformer;
import it.auties.optional.tree.Elements;
import it.auties.optional.tree.Maker;
import it.auties.optional.util.IllegalReflection;
import it.auties.optional.util.OptionalManager;
import lombok.RequiredArgsConstructor;
import lombok.experimental.ExtensionMethod;

import java.util.Objects;
import java.util.Optional;

import static com.sun.tools.javac.tree.TreeInfo.symbolFor;
import static com.sun.tools.javac.util.List.nil;

@RequiredArgsConstructor
@ExtensionMethod(IllegalReflection.class)
public class OptionalTranslator extends TreeTranslator {
    private final Maker maker;
    private final Types types;
    private final OptionalManager manager;
    private JCTree.JCClassDecl enclosingClass;
    private JCTree.JCMethodDecl enclosingMethod;

    @Override
    public <T extends JCTree> T translate(T tree) {
        removeOptional(tree);
        return super.translate(tree);
    }

    @Override
    public <T extends JCTree> List<T> translate(List<T> trees) {
        trees.forEach(this::removeOptional);
        return super.translate(trees);
    }

    @Override
    public List<JCTree.JCTypeParameter> translateTypeParams(List<JCTree.JCTypeParameter> trees) {
        trees.forEach(this::removeOptional);
        return super.translateTypeParams(trees);
    }

    @Override
    public List<JCTree.JCVariableDecl> translateVarDefs(List<JCTree.JCVariableDecl> trees) {
        trees.forEach(this::removeOptional);
        return super.translateVarDefs(trees);
    }

    private void removeOptional(JCTree tree) {
        if(tree == null){
            return;
        }

        var type = tree.type;
        if(type == null){
            return;
        }

        var element = type.asElement();
        if(element == null || !maker.hasOptionalName(element.getQualifiedName())){
            return;
        }

        tree.type = maker.unboxWrapper(tree.type);
    }

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
        tree.vartype = maker.typeExpression(optionalType);
    }

    private Type findOptionalVariableType(JCTree.JCVariableDecl variable){
        var optionalType = maker.unboxWrapper(variable.type);
        if(optionalType != null){
            return types.removeWildcards(optionalType);
        }

        var initializer = Objects.requireNonNull(variable.getInitializer(), "findOptionalVariableType: null initializer");
        return types.removeWildcards(maker.unboxWrapper(initializer.type));
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        this.enclosingMethod = tree;
        super.visitMethodDef(tree);
        var returnType = findMethodReturnType(tree);
        if(!isOptionalClass(returnType.asElement())){
            return;
        }

        var optionalType = maker.unboxWrapper(returnType);
        tree.restype = maker.typeExpression(optionalType);
        ((Type.MethodType) tree.type).restype = optionalType;
        ((Type.MethodType) tree.sym.type).restype = optionalType;
    }

    private Type findMethodReturnType(JCTree.JCMethodDecl method){
        return Optional.of(method.sym.getReturnType())
                .map(types::removeWildcards)
                .orElseThrow();
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
        super.visitApply(tree);
        var selected = symbolFor(tree);
        if (!isOwnedByOptional(selected)) {
            return;
        }

        var caller = Elements.getCallerExpression(tree);
        this.result = desugarOptionalInvocation(caller, caller.type, selected, tree.getArguments());
    }

    @Override
    public void visitReference(JCTree.JCMemberReference tree) {
        super.visitReference(tree);
        if (!isOwnedByOptional(tree.sym)) {
            return;
        }

        var invocation = desugarOptionalInvocation(null, Elements.getReturnType(tree.referentType), tree.sym, nil());
        var invocationSymbol = TreeInfo.symbolFor(invocation);
        var reference = maker.reference(invocationSymbol, enclosingClass.sym);
        var referenceType = (Type.ClassType) tree.type;
        referenceType.typarams_field = translateReferenceType(invocation, referenceType);
        reference.type = referenceType;
        reference.target = referenceType;
        reference.referentType = invocation.type;
        reference.sym = invocationSymbol;
        reference.ownerAccessible = true;
        this.result = reference;
    }

    private List<Type> translateReferenceType(JCTree.JCExpression invocation, Type.ClassType referenceType) {
        return referenceType.typarams_field.stream()
                .map(type -> maker.hasOptionalName(type.asElement().getQualifiedName()) ? maker.boxed(Elements.getReturnType(invocation.type)) : type)
                .collect(List.collector());
    }

    private JCTree.JCExpression desugarOptionalInvocation(JCTree.JCExpression caller, Type callerType, Symbol selected, List<JCTree.JCExpression> arguments) {
        var selectedName = selected.getSimpleName().toString();
        return manager.transformers()
                .stream()
                .filter(transformer -> transformer.supportedInstructions().contains(selectedName))
                .findFirst()
                .map(transformer -> transformTree(transformer, caller, callerType, selectedName, arguments))
                .orElseThrow(() -> new UnsupportedOperationException("No transformer could transform %s".formatted(selectedName)));
    }

    private JCTree.JCExpression transformTree(OptionalTransformer transformer, JCTree.JCExpression caller, Type callerType, String selectedName, List<JCTree.JCExpression> arguments) {
        return transformer.instruction(selectedName)
                .enclosingClass(enclosingClass.sym)
                .enclosingMethod(enclosingMethod)
                .invocationCaller(caller)
                .invocationCallerType(callerType)
                .invocationArguments(arguments)
                .transform();
    }

    private boolean isOwnedByOptional(Symbol selected) {
        return selected.getEnclosingElement() instanceof Symbol.ClassSymbol classSymbol
                && maker.hasOptionalName(classSymbol.getQualifiedName());
    }

    private boolean isOptionalClass(Symbol symbol){
        if(symbol == null){
            return false;
        }

        if(symbol instanceof Symbol.MethodSymbol method){
            return maker.hasOptionalName(method.getReturnType().asElement().getQualifiedName());
        }

        if(symbol instanceof Symbol.VarSymbol variable){
            return maker.hasOptionalName((variable.asType().asElement().getQualifiedName()));
        }

        return maker.hasOptionalName(symbol.getQualifiedName()) || isOptionalClass(symbol.getEnclosingElement());
    }
}
