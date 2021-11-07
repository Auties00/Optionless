package it.auties.optional.util;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import it.auties.optional.tree.Elements;
import it.auties.optional.tree.Maker;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.Iterator;
import java.util.Objects;

import static com.sun.tools.javac.util.List.of;

@RequiredArgsConstructor
public class LambdaDesugarer extends TreeScanner<JCTree.JCMethodDecl, Void> {
    private final Maker maker;
    private final Symbol.ClassSymbol enclosingClass;
    private final JCTree.JCMethodDecl enclosingMethod;
    private int counter;

    @Override
    public JCTree.JCMethodDecl visitLambdaExpression(LambdaExpressionTree node, Void unused) {
        var lambda = (JCTree.JCLambda) node;
        var parameters = createParameters(lambda);
        var body = createBody(lambda);
        var returnType = maker.unboxOptional(lambda.getDescriptorType(maker.types()).getReturnType());
        return maker.createMethod(enclosingClass, enclosingMethod, returnType, "lambda$%s".formatted(counter++), parameters, body, false);
    }

    @Override
    public JCTree.JCMethodDecl visitMemberReference(MemberReferenceTree node, Void unused) {
        var reference = (JCTree.JCMemberReference) node;
        var parameters = createParameters(reference);
        var body = createBody(reference, parameters);
        var returnType = maker.unboxOptional(reference.getDescriptorType(maker.types()).getReturnType());
        return maker.createMethod(enclosingClass, enclosingMethod, returnType, "reference$%s".formatted(counter++), parameters, maker.trees().Block(0L, of(body)), false);
    }

    private List<JCTree.JCVariableDecl> createParameters(JCTree.JCFunctionalExpression expression){
        var parameters = switch (expression){
            case JCTree.JCLambda lambda -> {
                var paramsIterator = lambda.params.iterator();
                yield lambda.getDescriptorType(maker.types())
                        .getParameterTypes()
                        .stream()
                        .map(type -> createLambdaParameter(paramsIterator, type))
                        .toList();
            }

            case JCTree.JCMemberReference reference -> ((Symbol.MethodSymbol) reference.sym).getParameters()
                    .stream()
                    .map(parameter -> parameter.type)
                    .map(type -> maker.createInferredParameter(null, type))
                    .toList();

            default -> throw new IllegalStateException("Cannot create parameters for unknown functional expression: " + expression);
        };

        completeWithScopedParameters(expression, parameters);
        return List.from(parameters);
    }

    private void completeWithScopedParameters(JCTree.JCFunctionalExpression expression, java.util.List<JCTree.JCVariableDecl> parameters) {
        new ContextualIdentityScanner()
                .scan(expression, null)
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

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    private class ContextualIdentityScanner extends TreeScanner<List<JCTree.JCIdent>, Void> {
        private final ListBuffer<JCTree.JCIdent> identifiers;
        public ContextualIdentityScanner() {
            this(new ListBuffer<>());
        }

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
            super.scan(tree, unused);
            return identifiers.toList();
        }

        @Override
        public List<JCTree.JCIdent> scan(Iterable<? extends Tree> nodes, Void unused) {
            super.scan(nodes, unused);
            return identifiers.toList();
        }
    }
}
