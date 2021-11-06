package it.auties.optional.plugin;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Names;
import it.auties.optional.transformer.*;
import it.auties.optional.tree.Maker;
import it.auties.optional.util.ModuleOpener;
import it.auties.optional.util.OptionalManager;

import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;

@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class OptionalPlugin implements Plugin, TaskListener {
    private OptionalTranslator translator;

    @Override
    public String getName() {
        return "FastOptional";
    }

    @Override
    public void init(JavacTask task, String... args) {
        ModuleOpener.openJavac();
        var context = ((BasicJavacTask) task).getContext();
        var names = Names.instance(context);
        var types = Types.instance(context);
        var maker = TreeMaker.instance(context);
        var symtab = Symtab.instance(context);
        var simpleMaker = new Maker(maker, names, symtab, types);
        var manager = initializeManager(maker, simpleMaker);
        this.translator = new OptionalTranslator(simpleMaker, types, manager);
        task.addTaskListener(this);
    }

    private OptionalManager initializeManager(TreeMaker maker, Maker simpleMaker) {
        return OptionalManager.instance()
                .addTransformer(new BangTransformer(maker, simpleMaker))
                .addTransformer(new ConditionalTransformer(maker, simpleMaker))
                .addTransformer(new ElvisTransformer(maker, simpleMaker))
                .addTransformer(new FilterTransformer(maker, simpleMaker))
                .addTransformer(new MapTransformer(maker, simpleMaker))
                .addTransformer(new NamedConstructorTransformer(maker, simpleMaker))
                .addTransformer(new OrTransformer(maker, simpleMaker))
                .addTransformer(new StreamTransformer(maker, simpleMaker));
    }

    @Override
    public void finished(TaskEvent event) {
        if (event.getKind() != TaskEvent.Kind.ANALYZE) {
            return;
        }

        try {
            var unit = (JCTree.JCCompilationUnit) event.getCompilationUnit();
            translator.translate(unit);
            System.err.println(unit);
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
