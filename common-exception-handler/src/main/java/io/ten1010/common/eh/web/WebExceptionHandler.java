package io.ten1010.common.eh.web;

import io.ten1010.common.eh.ExceptionHandler;
import io.ten1010.common.eh.ExceptionMetadataRegistry;
import io.ten1010.common.eh.log.LoggerProvider;

public class WebExceptionHandler extends ExceptionHandler<WebRequest, WebResponse> {
    public WebExceptionHandler(ExceptionMetadataRegistry<WebRequest, WebResponse> metadataRegistry, LoggerProvider loggerProvider) {
        super(metadataRegistry, loggerProvider, new WebRequestLogMessageFactory());
    }
}
