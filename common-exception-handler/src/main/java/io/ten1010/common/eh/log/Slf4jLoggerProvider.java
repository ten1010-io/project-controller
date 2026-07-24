package io.ten1010.common.eh.log;

import org.slf4j.LoggerFactory;

public class Slf4jLoggerProvider extends AbstractLoggerProvider {
    public Slf4jLoggerProvider(String topic) {
        super(topic);
    }

    @Override
    protected Logger createLogger(LogLevelEnum logLevelEnum) {
        org.slf4j.Logger baseLogger = LoggerFactory.getLogger(this.topic);
        return switch (logLevelEnum) {
            case ERROR -> baseLogger::error;
            case WARN -> baseLogger::warn;
            case INFO -> baseLogger::info;
            case DEBUG -> baseLogger::debug;
            case TRACE -> baseLogger::trace;
            case OFF -> message -> {};
        };
    }
}
