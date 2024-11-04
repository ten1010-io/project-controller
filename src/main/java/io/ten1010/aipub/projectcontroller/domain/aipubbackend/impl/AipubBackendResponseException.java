package io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl;

import io.ten1010.common.apiclient.ApiResponse;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Getter
public class AipubBackendResponseException extends RuntimeException {

    private static final List<String> STRING_TYPES = List.of("application/json", "text/html", "text/plain", "text/xml");

    private static String buildMessage(ApiResponse response) {
        String template = "statusCode=%d, headers=%s, body=%s";

        if (response.getBody() == null) {
            return String.format(template, response.getStatusCode(), response.getHeaders(), null);
        }

        if (hasStringContext(response)) {
            return String.format(template, response.getStatusCode(), response.getHeaders(), response.getBodyAsString().orElseThrow());
        }

        return String.format(template, response.getStatusCode(), response.getHeaders(), Arrays.toString(response.getBody()));
    }

    private static boolean hasStringContext(ApiResponse response) {
        Optional<String> headerOpt = getContentTypeHeader(response);
        return headerOpt.filter(STRING_TYPES::contains).isPresent();
    }

    private static Optional<String> getContentTypeHeader(ApiResponse response) {
        List<String> contentTypeHeaders = response.getHeaders().get("content-type");
        if (contentTypeHeaders.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(contentTypeHeaders.get(0));
    }

    private final ApiResponse response;

    public AipubBackendResponseException(ApiResponse response) {
        super(buildMessage(response));
        this.response = response;
    }

}
