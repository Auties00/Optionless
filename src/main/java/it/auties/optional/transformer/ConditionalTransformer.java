package it.auties.optional.transformer;

import com.sun.tools.javac.tree.JCTree;
import it.auties.optional.tree.Maker;

import java.util.Set;

public class ConditionalTransformer extends FunctionalTransformer{
    public ConditionalTransformer(Maker callMaker) {
        super(callMaker);
    }

    @Override
    public JCTree.JCStatement body() {
        var checkCondition = maker.createNullCheck(createIdentifierForParameter(0), true);
        var ifPresent = creteInstruction(0);
        var orElse = creteInstruction(1);
        return maker.trees()
                .If(checkCondition, execute(ifPresent), execute(orElse))
                .setType(maker.symtab().voidType);
    }

    private JCTree.JCExpression creteInstruction(int index) {
        if(index >= generatedInvocations.size()){
            return createIdentifierForParameter(index + 1);
        }

        return generatedInvocations.get(index);
    }

    private JCTree.JCStatement execute(JCTree.JCExpression expression) {
        return maker.trees()
                .Exec(expression)
                .setType(expression.type);
    }

    @Override
    public Set<String> supportedInstructions() {
        return Set.of("ifPresent", "ifPresentOrElse");
    }
}
