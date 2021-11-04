package it.auties.optional;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import java.util.HashSet;
import java.util.Set;

@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class OptionalPlugin implements Plugin, TaskListener {
    private Attr attr;
    private Enter enter;
    private Symtab symtab;
    private Names names;
    private JavacElements elements;
    private Types types;
    private TreeMaker maker;

    @Override
    public String getName() {
        return "FastOptional";
    }

    @Override
    public void init(JavacTask task, String... args) {
        var reflection = new IllegalReflection();
        reflection.openJavac();
        var context = ((BasicJavacTask) task).getContext();
        this.names = Names.instance(context);
        this.elements = JavacElements.instance(context);
        this.types = Types.instance(context);
        this.maker = TreeMaker.instance(context);
        this.attr = Attr.instance(context);
        this.enter = Enter.instance(context);
        this.symtab = Symtab.instance(context);
        task.addTaskListener(this);
    }

    @Override
    public void finished(TaskEvent event) {
        if (event.getKind() != TaskEvent.Kind.ANALYZE) {
            return;
        }

        try {
            var unit = (JCTree.JCCompilationUnit) event.getCompilationUnit();
            var translator = new OptionalTranslator(maker, types, symtab, names);
            translator.translate(unit);
            System.err.println(unit);
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
