package io.ten1010.common.eh.web;

import io.ten1010.common.eh.ExceptionMetadata;
import io.ten1010.common.eh.ExceptionMetadataRegistry;
import io.ten1010.common.eh.ResponseFactory;
import io.ten1010.common.eh.log.LogLevelEnum;
import io.ten1010.common.eh.log.LogLevelResolver;

public class ExceptionWebMetadataRegistrar implements WebMetadataRegistrar {
    @Override
    public void register(ExceptionMetadataRegistry<WebRequest, WebResponse> registry) {
        LogLevelResolver<WebRequest> logLevelResolver = (request, e) -> LogLevelEnum.ERROR;
        ResponseFactory<WebRequest, WebResponse> responseFactory = (request, e) -> new WebResponse(e, 500, new WebResponseBody("INTERNAL_SERVER_ERROR", null));
        registry.register(Exception.class, new ExceptionMetadata<>(logLevelResolver, responseFactory));
    }
}
