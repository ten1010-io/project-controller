package io.ten1010.common.eh.log;

import io.ten1010.common.eh.Request;

public interface LogMessageFactory<Req extends Request> {
    String createMessage(Req request, Exception e);
}
