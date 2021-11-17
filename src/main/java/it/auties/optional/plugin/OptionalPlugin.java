package it.auties.optional.plugin;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.Operators;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Names;
import it.auties.optional.transformer.*;
import it.auties.optional.tree.Maker;
import it.auties.optional.util.DebugTools;
import it.auties.optional.util.IllegalReflection;
import it.auties.optional.util.OptionalManager;

import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;

@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class OptionalPlugin implements Plugin, TaskListener {
    private OptionalTranslator translator;
    private DebugTools debugTools;

    @Override
    public String getName() {
        return "Optional";
    }

    @Override
    public void init(JavacTask task, String... args) {
        IllegalReflection.openJavac();
        var context = ((BasicJavacTask) task).getContext();
        var names = Names.instance(context);
        var types = Types.instance(context);
        var symtab = Symtab.instance(context);
        var operators = Operators.instance(context);
        var maker = TreeMaker.instance(context);
        var attr = Attr.instance(context);
        var simpleMaker = new Maker(maker, names, symtab, attr, types, operators);
        var manager = initializeManager(simpleMaker);
        this.translator = new OptionalTranslator(simpleMaker, types, manager);
        this.debugTools = new DebugTools(args);
        task.addTaskListener(this);
    }

    @Override
    public void finished(TaskEvent event) {
        if (event.getKind() != TaskEvent.Kind.ANALYZE) {
            return;
        }

        var unit = (JCTree.JCCompilationUnit) event.getCompilationUnit();
        translator.translate(unit);
        debugTools.debug(() -> System.err.println(unit));
    }

    @Override
    public boolean autoStart() {
        return true;
    }

    private OptionalManager initializeManager(Maker simpleMaker) {
        return OptionalManager.instance()
                .addTransformer(new BangTransformer(simpleMaker))
                .addTransformer(new ConditionalTransformer(simpleMaker))
                .addTransformer(new ValueTransformer(simpleMaker))
                .addTransformer(new ElvisTransformer(simpleMaker))
                .addTransformer(new FilterTransformer(simpleMaker))
                .addTransformer(new MapTransformer(simpleMaker))
                .addTransformer(new NamedConstructorTransformer(simpleMaker))
                .addTransformer(new OrTransformer(simpleMaker))
                .addTransformer(new StreamTransformer(simpleMaker));
    }
}
