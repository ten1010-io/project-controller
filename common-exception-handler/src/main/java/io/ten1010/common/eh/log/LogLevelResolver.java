package io.ten1010.common.eh.log;

import io.ten1010.common.eh.Request;

public interface LogLevelResolver<Req extends Request> {
    LogLevelEnum resolve(Req request, Exception e);
}
