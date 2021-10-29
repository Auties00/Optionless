package it.auties.optional;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;

@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class OptionalPlugin implements Plugin, TaskListener {
    private IllegalReflection reflection;
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
        this.reflection = new IllegalReflection().openJavac();
        var context = ((BasicJavacTask) task).getContext();
        this.names = Names.instance(context);
        this.elements = JavacElements.instance(context);
        this.types = Types.instance(context);
        this.maker = TreeMaker.instance(context);
        task.addTaskListener(this);
    }
    
    @Override
    public void finished(TaskEvent event) {
       try{
           if (event.getKind() != TaskEvent.Kind.ANALYZE) {
               return;
           }

           var unit = (JCTree.JCCompilationUnit) event.getCompilationUnit();
           new OptionalTranslator(maker, types, elements, names).translate(unit);
           System.err.println(unit);
       }catch (Throwable throwable){
           throwable.printStackTrace();
           throw new RuntimeException(throwable);
       }
    }
}
