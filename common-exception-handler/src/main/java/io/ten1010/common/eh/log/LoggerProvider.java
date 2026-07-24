package io.ten1010.common.eh.log;

public interface LoggerProvider {
    Logger get(LogLevelEnum logLevelEnum);
}
