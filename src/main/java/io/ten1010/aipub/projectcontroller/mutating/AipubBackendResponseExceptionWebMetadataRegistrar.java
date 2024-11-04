package io.ten1010.aipub.projectcontroller.mutating;

import io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl.AipubBackendResponseException;
import io.ten1010.common.eh.ExceptionMetadata;
import io.ten1010.common.eh.ExceptionMetadataRegistry;
import io.ten1010.common.eh.ResponseFactory;
import io.ten1010.common.eh.log.LogLevelEnum;
import io.ten1010.common.eh.log.LogLevelResolver;
import io.ten1010.common.eh.web.WebMetadataRegistrar;
import io.ten1010.common.eh.web.WebRequest;
import io.ten1010.common.eh.web.WebResponse;
import io.ten1010.common.eh.web.WebResponseBody;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class AipubBackendResponseExceptionWebMetadataRegistrar implements WebMetadataRegistrar {

    @Override
    public void register(ExceptionMetadataRegistry<WebRequest, WebResponse> registry) {
        LogLevelResolver<WebRequest> logLevelResolver = (request, e) -> LogLevelEnum.ERROR;
        ResponseFactory<WebRequest, WebResponse> responseFactory =
                (request, e) -> new WebResponse(e, 500, new WebResponseBody("AIPUB_BACKEND_RESPONSE_ERROR", null));
        registry.register(AipubBackendResponseException.class, new ExceptionMetadata<>(logLevelResolver, responseFactory));
    }

}
