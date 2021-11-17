package it.auties.optional.transformer;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import it.auties.optional.tree.Maker;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Set;

@RequiredArgsConstructor
@Data
@Accessors(fluent = true, chain = true)
public abstract class OptionalTransformer {
    protected final Maker maker;
    protected String instruction;
    protected JCTree.JCExpression invocationCaller;
    protected Type invocationCallerType;
    protected List<JCTree.JCExpression> invocationArguments;
    protected JCTree.JCMethodDecl enclosingMethod;
    protected Symbol.ClassSymbol enclosingClass;

    public boolean isMemberReferenceScoped(){
        return invocationCaller == null;
    }

    public abstract JCTree.JCExpression transform();
    public abstract Set<String> supportedInstructions();
}
