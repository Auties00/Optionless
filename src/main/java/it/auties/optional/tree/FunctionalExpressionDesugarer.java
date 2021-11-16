package it.auties.optional.tree;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.sun.tools.javac.util.List.of;

@RequiredArgsConstructor
public class FunctionalExpressionDesugarer extends TreeScanner<JCTree.JCMethodDecl, Void> {
    private final Maker maker;
    private final Symbol.ClassSymbol enclosingClass;
    private final JCTree.JCMethodDecl enclosingMethod;
    private final IdentifierTranslator identifierTranslator = new IdentifierTranslator();

    @Override
    public JCTree.JCMethodDecl visitLambdaExpression(LambdaExpressionTree node, Void unused) {
        var lambda = (JCTree.JCLambda) node;
        var parameters = createParameters(lambda);
        var body = createBody(lambda);
        var returnType = maker.unboxWrapper(lambda.getDescriptorType(maker.types()).getReturnType());
        var method = maker.newMethod()
                .enclosingClass(enclosingClass)
                .modelMethod(enclosingMethod)
                .originalType(lambda.type)
                .returnType(returnType)
                .name("lambda")
                .parameters(parameters)
                .body(body)
                .toTree();
        return identifierTranslator.redefine(method);
    }

    @Override
    public JCTree.JCMethodDecl visitMemberReference(MemberReferenceTree node, Void unused) {
        var reference = (JCTree.JCMemberReference) node;
        var parameters = createParameters(reference);
        var body = createBody(reference, parameters);
        var returnType = maker.unboxWrapper(reference.getDescriptorType(maker.types()).getReturnType());
        var method = maker.newMethod()
                .enclosingClass(enclosingClass)
                .modelMethod(enclosingMethod)
                .originalType(reference.type)
                .returnType(returnType)
                .name("reference")
                .parameters(parameters)
                .body(maker.trees().Block(0L, of(body)))
                .toTree();
        return identifierTranslator.redefine(method);
    }

    private List<JCTree.JCVariableDecl> createParameters(JCTree.JCFunctionalExpression expression){
        var parameters = switch (expression){
            case JCTree.JCLambda lambda -> {
                var paramsIterator = lambda.params.iterator();
                yield lambda.getDescriptorType(maker.types())
                        .getParameterTypes()
                        .stream()
                        .map(parameter -> createLambdaParameter(paramsIterator, parameter))
                        .collect(Collectors.toList());
            }

            case JCTree.JCMemberReference reference -> {
                var referenceParameters = ((Symbol.MethodSymbol) reference.sym).getParameters();
                yield referenceParameters
                        .stream()
                        .map(parameter -> maker.createInferredParameter(parameter.type))
                        .collect(Collectors.toList());
            }

            default -> throw new IllegalStateException("Cannot create parameters for unknown functional expression: " + expression);
        };

        completeWithScopedParameters(expression, parameters);
        return List.from(parameters);
    }

    private void completeWithScopedParameters(JCTree.JCFunctionalExpression expression, Collection<JCTree.JCVariableDecl> parameters) {
        new ContextualIdentityScanner().scan(expression, null)
                .stream()
                .filter(identifier -> parameters.stream().noneMatch(result -> Objects.equals(result.sym.asType(), identifier.type)))
                .map(maker::createParameterFromIdentifier)
                .forEachOrdered(parameters::add);
    }

    private JCTree.JCVariableDecl createLambdaParameter(Iterator<JCTree.JCVariableDecl> paramsIterator, Type type) {
        var parameter = paramsIterator.hasNext() ? paramsIterator.next().getName() : null;
        return maker.createInferredParameter(parameter, type);
    }

    private JCTree.JCBlock createBody(JCTree.JCLambda lambda) {
        return switch (lambda.getBodyKind()){
            case STATEMENT -> (JCTree.JCBlock) lambda.getBody();
            case EXPRESSION -> {
                var expression = (JCTree.JCExpression) lambda.getBody();
                var parsedExpression = Elements.isVoid(expression.type.asElement()) ? maker.trees().Exec(expression) : maker.trees().Return(expression);
                yield maker.trees().Block(0L, of(parsedExpression.setType(expression.type)));
            }
        };
    }

    private JCTree.JCStatement createBody(JCTree.JCMemberReference reference, List<JCTree.JCVariableDecl> parameters) {
        if(reference.sym.getSimpleName().equals(maker.names().init)){
            var newInstance = maker.trees().Create(reference.sym, parameters.map(maker.trees()::Ident));
            return maker.trees().Return(newInstance);
        }

        var selected = maker.trees().Select(reference.getQualifierExpression(), reference.sym);
        var result = maker.trees().App(selected, parameters.map(maker.trees()::Ident));
        return Elements.isVoid(reference.sym) ? maker.trees().Exec(result) : maker.trees().Return(result);
    }

    private class ContextualIdentityScanner extends TreeScanner<List<JCTree.JCIdent>, Void> {
        private final ListBuffer<JCTree.JCIdent> identifiers = new ListBuffer<>();

        @Override
        public List<JCTree.JCIdent> visitIdentifier(IdentifierTree node, Void unused) {
            var tree = (JCTree.JCIdent) node;
            if(!(tree.sym instanceof Symbol.VarSymbol)){
                return super.visitIdentifier(node, unused);
            }

            if(!Objects.equals(tree.sym.owner, enclosingMethod.sym)){
                return super.visitIdentifier(node, unused);
            }

            identifiers.add(tree);
            return super.visitIdentifier(node, unused);
        }

        @Override
        public List<JCTree.JCIdent> scan(Tree tree, Void unused) {
            identifiers.clear();
            super.scan(tree, unused);
            return identifiers.toList();
        }

        @Override
        public List<JCTree.JCIdent> scan(Iterable<? extends Tree> nodes, Void unused) {
            identifiers.clear();
            super.scan(nodes, unused);
            return identifiers.toList();
        }
    }

    private class IdentifierTranslator extends TreeTranslator {
        private JCTree.JCMethodDecl method;
        public JCTree.JCMethodDecl redefine(JCTree.JCMethodDecl method){
            this.method = method;
            return translate(method);
        }

        @Override
        public void visitIdent(JCTree.JCIdent tree) {
            super.visitIdent(tree);
            var owner = tree.sym.getEnclosingElement();
            if(!Objects.equals(owner, enclosingClass) && !Objects.equals(owner, enclosingMethod.sym)){
                return;
            }

            findMatchingParameter(tree)
                    .map(maker::identifier)
                    .ifPresent(identifier -> this.result = identifier);
        }

        private Optional<Symbol.VarSymbol> findMatchingParameter(JCTree.JCIdent tree) {
            return method.params.stream()
                    .filter(parameter -> parameter.getName().contentEquals(tree.name))
                    .map(parameter -> parameter.sym)
                    .findFirst();
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    @Accessors(fluent = true)
    public static class FunctionalExpressionType extends Type.MethodType{
        Type erased;
        public FunctionalExpressionType(MethodType type, Type erased) {
            super(type.argtypes, type.restype, type.thrown, type.tsym);
            this.erased = erased;
        }
    }
}
