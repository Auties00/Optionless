package it.auties.optional.util;

import lombok.experimental.UtilityClass;
import sun.misc.Unsafe;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.NoSuchElementException;

@UtilityClass
public class ModuleOpener {
    private final Unsafe unsafe = getUnsafe();

    private Unsafe getUnsafe() {
        try {
            var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            return (Unsafe) unsafeField.get(null);
        }catch (NoSuchFieldException exception){
            throw new NoSuchElementException("Cannot find unsafe field in wrapper class");
        }catch (IllegalAccessException exception){
            throw new UnsupportedOperationException("Access to the unsafe wrapper has been blocked: the day has come. In this future has the OpenJDK team created a publicly available compiler api that can do something? Probably not", exception);
        }
    }

    public void openJavac(){
        try {
            var jdkCompilerModule = getCompilerModule();
            var addOpensMethod = Module.class.getDeclaredMethod("implAddOpens", String.class, Module.class);
            var addOpensMethodOffset = unsafe.objectFieldOffset(ModulePlaceholder.class.getDeclaredField("first"));
            unsafe.putBooleanVolatile(addOpensMethod, addOpensMethodOffset, true);
            Arrays.stream(Package.getPackages())
                    .map(Package::getName)
                    .filter(pack -> pack.startsWith("com.sun.tools.javac"))
                    .forEach(pack -> invokeAccessibleMethod(addOpensMethod, jdkCompilerModule, pack, ModuleOpener.class.getModule()));
        }catch (Throwable throwable){
            throw new UnsupportedOperationException("Cannot open Javac Modules", throwable);
        }
    }

    private Module getCompilerModule() {
        return ModuleLayer.boot()
                .findModule("jdk.compiler")
                .orElseThrow(() -> new ExceptionInInitializerError("Missing module: jdk.compiler"));
    }

    private void invokeAccessibleMethod(Method method, Object caller, Object... arguments){
        try {
            method.invoke(caller, arguments);
        }catch (Throwable throwable){
            throw new RuntimeException("Cannot invoke accessible method", throwable);
        }
    }

    @SuppressWarnings("all")
    public static class ModulePlaceholder {
        boolean first;
        static final Object staticObj = OutputStream.class;
        volatile Object second;
        private static volatile boolean staticSecond;
        private static volatile boolean staticThird;
    }
}
