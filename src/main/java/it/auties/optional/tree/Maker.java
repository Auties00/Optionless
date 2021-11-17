package it.auties.optional.tree;

import com.sun.source.tree.MemberReferenceTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.Operators;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import it.auties.optional.util.IllegalReflection;
import it.auties.optional.util.OptionalManager;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.experimental.ExtensionMethod;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.sun.tools.javac.code.TypeTag.BOT;
import static com.sun.tools.javac.util.List.nil;
import static com.sun.tools.javac.util.List.of;

@AllArgsConstructor
@Data
@Accessors(fluent = true)
@ExtensionMethod(IllegalReflection.class)
public class Maker {
    private final TreeMaker trees;
    protected final Names names;
    protected final Symtab symtab;
    protected final Attr attr;
    protected final Types types;
    private final Operators operators;
    private Type streamType;
    private Symbol.ClassSymbol noSuchElementSymbol;

    public Maker(TreeMaker trees, Names names, Symtab symtab, Attr attr, Types types, Operators operators) {
        this(trees, names, symtab, attr, types, operators, null, null);
    }

    public JCTree.JCExpression thisIdentifier(Type type){
        return trees.This(type);
    }

    public JCTree.JCIdent identifier(Symbol symbol){
        return trees.Ident(symbol);
    }

    public JCTree.JCExpression typeExpression(Type type){
        return trees.Type(type);
    }

    public JCTree.JCMethodInvocation createCallOnIdentifier(JCTree.JCMethodDecl method, List<JCTree.JCExpression> parameters) {
        var identifier = identifier(Objects.requireNonNull(method.sym, "Invalid symbol"));
        return trees.App(identifier, parameters);
    }

    public JCTree.JCStatement createThrowNoSuchElementException(){
        if(noSuchElementSymbol == null){
            this.noSuchElementSymbol = findBaseModuleClassSymbol(NoSuchElementException.class);
        }

        var constructor = noSuchElementSymbol.members().findFirst(names.init);
        var instance = (JCTree.JCNewClass) trees.Create(constructor, of(trees.Literal("No value present")));
        instance.constructorType = noSuchElementSymbol.asType();
        return trees.Throw(instance)
                .setType(noSuchElementSymbol.asType());
    }

    public JCTree.JCMethodInvocation makeStream(JCTree.JCExpression argument){
        if(streamType == null){
            this.streamType = findBaseModuleClassSymbol(Stream.class).asType();
        }

        var members = Objects.requireNonNull(streamType.asElement().members(), "The stream class has no members");
        var methodSymbol = members.findFirst(names.fromString("ofNullable"));
        var selected = trees.Select(typeExpression(streamType), methodSymbol);
        return trees.App(selected, argument == null ? nil() : of(argument));
    }

    public JCTree.JCVariableDecl createInferredParameter(Type type) {
        return createInferredParameter(null, type);
    }

    public JCTree.JCVariableDecl createInferredParameter(Name name, Type type) {
        var param = trees.Param(Objects.requireNonNullElse(name, uniqueName("inferred")), unboxWrapper(type), null);
        param.sym.adr = 0;
        return param;
    }

    public JCTree.JCVariableDecl createParameterFromIdentifier(JCTree.JCIdent identifier) {
        var param = trees.Param(identifier.getName(), identifier.type, null);
        param.sym.adr = 0;
        return param;
    }

    public Type unboxWrapper(Type type) {
        if(type == null){
            return null;
        }

        if(type.getTypeArguments().isEmpty()){
            return types.erasure(type);
        }

        if(types.isFunctionalInterface(type)){
            return unboxWrapper(unboxFunctionalInterface(type));
        }

        var erased = types.erasure(type.getTypeArguments().head);
        if(hasOptionalName(erased.asElement().getQualifiedName())){
            return unboxWrapper(erased);
        }

        return erased;
    }

    private Type unboxFunctionalInterface(Type wrapperType) {
        var functionalInterfaceType = wrapperType.asElement().asType();
        var functionalInterfaceMethod = Elements.getFunctionalInterfaceMethod(wrapperType.asElement()).getReturnType();
        return IntStream.range(0, functionalInterfaceType.getTypeArguments().size())
                .filter(index -> types.isSameType(functionalInterfaceMethod, functionalInterfaceType.getTypeArguments().get(index)))
                .mapToObj(wrapperType.getTypeArguments()::get)
                .findFirst()
                .orElse(functionalInterfaceMethod);
    }

    public boolean hasOptionalName(Name name) {
        return name.startsWith(names.fromString(Optional.class.getName()));
    }

    public JCTree.JCLiteral createNullType() {
        return trees.Literal(BOT, null)
                .setType(symtab.botType);
    }

    private Symbol.ClassSymbol findBaseModuleClassSymbol(Class<?> clazz) {
        var baseModule = symtab.getModule(names.fromString("java.base"));
        var className = names.fromString(clazz.getName());
        return symtab.getClass(baseModule, className);
    }

    private void eraseAndBoxParameters(List<JCTree.JCVariableDecl> parameters, boolean box) {
        parameters.forEach(parameter -> {
            parameter.type = eraseAndBox(parameter.type, box);
            parameter.sym.type = eraseAndBox(parameter.sym.type, box);
        });
    }

    public Type eraseAndBox(Type type, boolean box) {
        return Optional.ofNullable(type)
                .map(safeType -> box ? types.boxedTypeOrType(types.erasure(safeType)) : types.erasure(safeType))
                .orElse(null);
    }

    public JCTree.JCExpression createNullAssert(JCTree.JCExpression left){
        return attr.makeNullCheck(left);
    }

    public JCTree.JCBinary createNullCheck(JCTree.JCExpression left, boolean nonNull){
        left.type = eraseAndBox(left.type, true);
        var tag = nonNull ? JCTree.Tag.NE : JCTree.Tag.EQ;
        var nullType = createNullType();
        var binary = trees.Binary(tag , left, nullType);
        binary.operator = resolveBinary(left, nullType, tag);
        binary.type = symtab.booleanType;
        return binary;
    }

    public JCTree.JCExpression createDummyNullCheck(boolean nonNull){
        var checkMethod = symtab.objectsType
                .asElement()
                .members()
                .findFirst(names.fromString(nonNull ? "nonNull" : "isNull"));
        return trees.App(trees.Select(trees.Type(symtab.objectsType), checkMethod), nil());
    }

    @SneakyThrows
    private Symbol.OperatorSymbol resolveBinary(JCTree.JCExpression left, JCTree.JCExpression right, JCTree.Tag tag) {
        return (Symbol.OperatorSymbol) operators.getClass()
                .getDeclaredMethod("resolveBinary", JCDiagnostic.DiagnosticPosition.class, JCTree.Tag.class, Type.class, Type.class)
                .opened()
                .invoke(operators, left.pos(), tag, left.type, right.type);
    }

    public JCTree.JCMethodDecl createMethodFromLambda(Symbol.ClassSymbol enclosingClass, JCTree.JCMethodDecl enclosingMethod, JCTree.JCExpression expression) {
        var desugarer = new FunctionalExpressionDesugarer(this, enclosingClass, enclosingMethod);
        return desugarer.scan(expression, null);
    }

    public Name uniqueName(String name) {
        var counter = OptionalManager.instance().counter().getAndIncrement();
        return names.fromString(name + "$" + counter);
    }

    public Type boxed(Type type){
        return types.boxedTypeOrType(type);
    }

    public JCTree.JCMemberReference reference(Symbol selected, Symbol.ClassSymbol enclosingClass){
        var thisScope = selected.getEnclosingElement().equals(enclosingClass);
        var caller = referenceCaller(selected, thisScope);
        var reference = trees.Reference(MemberReferenceTree.ReferenceMode.INVOKE, selected.getSimpleName(), caller, null);
        reference.kind = thisScope ? JCTree.JCMemberReference.ReferenceKind.BOUND : JCTree.JCMemberReference.ReferenceKind.UNBOUND;
        return reference;
    }

    private JCTree.JCExpression referenceCaller(Symbol selected, boolean thisScope) {
        return thisScope ? thisIdentifier(selected.getEnclosingElement().asType())
                : typeExpression(selected.getEnclosingElement().asType());
    }

    public MethodBuilder newMethod(){
        return new MethodBuilder();
    }

    @NoArgsConstructor
    @Data
    @Accessors(fluent = true, chain = true)
    public class MethodBuilder {
        Symbol.ClassSymbol enclosingClass;
        JCTree.JCMethodDecl modelMethod;
        Type originalType;
        Type returnType;
        String name;
        List<JCTree.JCVariableDecl> parameters;
        JCTree.JCBlock body;

        public JCTree.JCMethodDecl toTree() {
            eraseAndBoxParameters(parameters, false);
            var methodType = createMethodType();
            var methodSymbol = createMethodSymbol(methodType);
            methodSymbol.params = parameters.map(parameter -> parameter.sym);
            var method = trees.at(modelMethod.pos()).MethodDef(methodSymbol, body);
            parameters.forEach(param -> param.sym.owner = methodSymbol);
            return OptionalManager.instance().addLambda(method);
        }

        private Type.MethodType createMethodType() {
            var methodType = new Type.MethodType(parameters.map(param -> param.type), eraseAndBox(returnType, false), nil(), symtab.methodClass);
            if (originalType == null) {
                return methodType;
            }

            return new FunctionalExpressionDesugarer.FunctionalExpressionType(methodType, originalType);
        }

        private Symbol.MethodSymbol createMethodSymbol(Type.MethodType methodType) {
            var modifiers = Elements.createModifiers(modelMethod);
            return new Symbol.MethodSymbol(modifiers, uniqueName(name), methodType, enclosingClass);
        }
    }
}
