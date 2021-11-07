package it.auties.optional.tree;

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
import it.auties.optional.util.LambdaDesugarer;
import it.auties.optional.util.OptionalManager;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
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
    private final Names names;
    private final Symtab symtab;
    private final Attr attr;
    private final Types types;
    private final Operators operators;
    private OptionalManager manager;
    private Type streamType;
    private Symbol.ClassSymbol noSuchElementSymbol;
    private int counter;

    public Maker(TreeMaker trees, Names names, Symtab symtab, Attr attr, Types types, Operators operators) {
        this(trees, names, symtab, attr, types, operators, OptionalManager.instance(), null, null, 0);;
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

    public JCTree.JCStatement createNoSuchElementException(){
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

    public JCTree.JCVariableDecl createInferredParameter(Name name, Type type) {
        var parameter = trees.Param(Objects.requireNonNullElse(name, names.fromString("inferred%s".formatted(counter++))), type, null);
        parameter.sym.adr = 0;
        return parameter;
    }

    public JCTree.JCVariableDecl createParameterFromIdentifier(JCTree.JCIdent identifier) {
        var parameter = trees.Param(identifier.getName(), identifier.sym.type, null);
        parameter.sym.adr = 0;
        return parameter;
    }

    public Type unboxOptional(Type returnType) {
        if(returnType == null){
            return null;
        }

        var head = returnType.getTypeArguments().head;
        if(head == null){
            return types.erasure(returnType);
        }

        var erased = types.erasure(head);
        if(erased.asElement().getQualifiedName().contentEquals(Optional.class.getName())){
            return unboxOptional(erased);
        }

        return erased;
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

    public JCTree.JCMethodDecl createMethod(Symbol.ClassSymbol enclosingClass, JCTree.JCMethodDecl enclosingMethod, Type returnType, String name, List<JCTree.JCVariableDecl> parameters, JCTree.JCBlock body, boolean box) {
        eraseAndBoxParameters(parameters, box);
        var methodName = names.fromString("%s%s".formatted(name, counter++));
        var methodType = new Type.MethodType(parameters.map(param -> param.type), eraseAndBox(returnType, box), nil(), enclosingClass);
        var methodSymbol = new Symbol.MethodSymbol(Elements.createModifiers(enclosingMethod), methodName, methodType, enclosingClass);
        methodSymbol.params = parameters.map(parameter -> parameter.sym);
        var method = trees.MethodDef(methodSymbol, body);
        parameters.forEach(param -> param.sym.owner = method.sym);
        return manager.addLambda(method);
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
        var desugarer = new LambdaDesugarer(this, enclosingClass, enclosingMethod);
        return desugarer.scan(expression, null);
    }
}
