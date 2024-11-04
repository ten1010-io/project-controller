package io.ten1010.aipub.projectcontroller.mutating;

import io.ten1010.common.eh.ExceptionMetadata;
import io.ten1010.common.eh.ExceptionMetadataRegistry;
import io.ten1010.common.eh.ResponseFactory;
import io.ten1010.common.eh.log.LogLevelResolver;
import io.ten1010.common.eh.web.WebMetadataRegistrar;
import io.ten1010.common.eh.web.WebRequest;
import io.ten1010.common.eh.web.WebResponse;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class AdmissionReviewExceptionWebMetadataRegistrar implements WebMetadataRegistrar {

    private final ExceptionMetadataRegistry<WebRequest, WebResponse> metadataRegistry;

    @Override
    public void register(ExceptionMetadataRegistry<WebRequest, WebResponse> registry) {
        ExceptionMetadata<WebRequest, WebResponse> metadata = new ExceptionMetadata<>(createLogLevelResolver(), createResponseFactory());
        registry.register(AdmissionReviewException.class, metadata);
    }

    private LogLevelResolver<WebRequest> createLogLevelResolver() {
        return (request, e) -> {
            if (e instanceof AdmissionReviewException reviewException) {
                Exception cause = reviewException.getCause();
                ExceptionMetadata<WebRequest, WebResponse> metadata = metadataRegistry.getCompatibleMetadata(cause.getClass()).orElseThrow();
                return metadata.getLogLevelResolver().resolve(request, cause);
            }
            throw new IllegalArgumentException();
        };
    }

    private ResponseFactory<WebRequest, WebResponse> createResponseFactory() {
        return (request, e) -> {
            if (e instanceof AdmissionReviewException reviewException) {
                Exception cause = reviewException.getCause();
                ExceptionMetadata<WebRequest, WebResponse> metadata = metadataRegistry.getCompatibleMetadata(cause.getClass()).orElseThrow();
                WebResponse response = metadata.getResponseFactory().create(request, cause);

                return new WebResponse(e, response.getHttpStatus(), response.getBody());
            }
            throw new IllegalArgumentException();
        };
    }

}
