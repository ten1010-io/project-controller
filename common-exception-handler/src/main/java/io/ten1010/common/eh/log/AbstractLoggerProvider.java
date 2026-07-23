package io.ten1010.common.eh.log;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractLoggerProvider implements LoggerProvider {
    protected final String topic;
    private final Map<LogLevelEnum, Logger> cache;

    public AbstractLoggerProvider(String topic) {
        this.topic = topic;
        this.cache = new HashMap<>();
    }

    @Override
    public Logger get(LogLevelEnum logLevelEnum) {
        if (this.cache.containsKey(logLevelEnum)) {
            return this.cache.get(logLevelEnum);
        }
        Logger created = this.createLogger(logLevelEnum);
        this.cache.put(logLevelEnum, created);
        return created;
    }

    protected abstract Logger createLogger(LogLevelEnum logLevelEnum);
}
