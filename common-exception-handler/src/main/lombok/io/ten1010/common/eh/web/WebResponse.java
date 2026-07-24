package io.ten1010.common.eh.web;

import io.ten1010.common.eh.AbstractResponse;
import lombok.Getter;

@Getter
public class WebResponse extends AbstractResponse {

    private final int httpStatus;
    private final WebResponseBody body;

    public WebResponse(Exception exception, int httpStatus, WebResponseBody body) {
        super(exception);
        this.httpStatus = httpStatus;
        this.body = body;
    }

}
