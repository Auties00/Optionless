package it.auties.optional.tree;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.Operators;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.*;
import it.auties.optional.util.IllegalReflection;
import it.auties.optional.util.OptionalManager;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.ExtensionMethod;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
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
    private OptionalManager manager;
    private Type streamType;
    private Symbol.ClassSymbol noSuchElementSymbol;

    public Maker(TreeMaker trees, Names names, Symtab symtab, Attr attr, Types types, Operators operators) {
        this(trees, names, symtab, attr, types, operators, OptionalManager.instance(), null, null);
    }

    public JCTree.JCIdent identifier(Symbol symbol){
        return trees.Ident(symbol);
    }

    public JCTree.JCMethodInvocation createCallOnIdentifier(JCTree.JCMethodDecl method, List<JCTree.JCExpression> parameters) {
        var identifier = identifier(Objects.requireNonNull(method.sym, "Invalid symbol"));
        return trees.App(identifier, parameters);
    }

    public JCTree.JCExpression createTypeExpression(Type type){
        return trees.Type(type);
    }

    public JCTree.JCStatement createThrowNoSuchElementException(){
        if(noSuchElementSymbol == null){
            this.noSuchElementSymbol = findBaseModuleClassSymbol(NoSuchElementException.class);
        }

        var ctor = noSuchElementSymbol.members().findFirst(names.init);
        return trees.Throw(trees.Create(ctor, of(trees.Literal("No value present"))))
                .setType(noSuchElementSymbol.asType());
    }

    public JCTree.JCExpression makeStream(JCTree.JCExpression argument){
        if(streamType == null){
            this.streamType = findBaseModuleClassSymbol(Stream.class).asType();
        }

        var members = Objects.requireNonNull(streamType.asElement().members(), "The stream class has no members");
        var methodSymbol = members.findFirst(names.fromString("ofNullable"), matchSymbol(argument));
        var selected = trees.Select(createTypeExpression(streamType), methodSymbol);
        return trees.App(selected, of(argument));
    }

    private Predicate<Symbol> matchSymbol(JCTree.JCExpression expression) {
        return symbol -> symbol.asType()
                .getParameterTypes()
                .stream()
                .allMatch(parameterType -> types.isAssignable(types.erasure(expression.type), types.erasure(parameterType)));
    }

    public JCTree.JCVariableDecl createInferredParameter(Type type) {
        return createInferredParameter(null, type);
    }

    public JCTree.JCVariableDecl createInferredParameter(Name name, Type type) {
        var param = trees.Param(Objects.requireNonNullElse(name, uniqueName("inferred")), unboxOptional(type), null);
        param.sym.adr = 0;
        return param;
    }

    public JCTree.JCVariableDecl createParameterFromIdentifier(JCTree.JCIdent identifier) {
        var param = trees.Param(identifier.getName(), identifier.type, null);
        param.sym.adr = 0;
        return param;
    }

    public Type unboxOptional(Type type) {
        if(type == null || !hasOptionalName(type.asElement().getQualifiedName())){
            return type;
        }

        var head = type.getTypeArguments().head;
        if(head == null){
            return types.erasure(type);
        }

        return unboxOptional(types.erasure(head));
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

    private Type eraseAndBox(Type type, boolean box) {
        return box ? types.boxedTypeOrType(types.erasure(type)) : types.erasure(type);
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
        var counter = manager.counter().getAndIncrement();
        return names.fromString(name + "$" + counter);
    }

    public Type boxed(Type type){
        return types.boxedTypeOrType(type);
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
            return manager.addLambda(method);
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
