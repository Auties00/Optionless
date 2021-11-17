package it.auties.optional.util;

import java.util.Arrays;

public record DebugTools(boolean debug) {
    public static final String DEBUG_FLAG = "debug";
    public DebugTools(String... args){
        this(Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase(DEBUG_FLAG)));
    }

    public void debug(Runnable runnable){
        if(!debug){
            return;
        }

        runnable.run();
    }
}
