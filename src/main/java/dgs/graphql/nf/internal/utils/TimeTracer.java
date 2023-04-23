package dgs.graphql.nf.internal.utils;

import java.util.function.Supplier;
import org.slf4j.Logger;

public final class TimeTracer {
    private TimeTracer() {}

    public static final TimeTracer INSTANCE = new TimeTracer();

    public final <R> R logTime(Supplier<? extends R> action,  Logger logger,  String message) {
        var startTime = System.currentTimeMillis();
        var result = action.get();
        var endTime = System.currentTimeMillis();
        var totalTime = endTime - startTime;
        logger.debug(message, totalTime);
        return result;
    }
}

