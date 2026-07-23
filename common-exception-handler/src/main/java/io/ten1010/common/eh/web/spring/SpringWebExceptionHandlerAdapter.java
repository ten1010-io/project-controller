package io.ten1010.common.eh.web.spring;

import io.ten1010.common.eh.web.WebExceptionHandler;
import io.ten1010.common.eh.web.WebRequest;
import io.ten1010.common.eh.web.WebResponse;
import org.springframework.http.ResponseEntity;

public class SpringWebExceptionHandlerAdapter {
    private final WebExceptionHandler webExceptionHandler;
    private SpringWebRequestConverter requestConverter;
    private SpringWebResponseConverter responseConverter;

    public SpringWebExceptionHandlerAdapter(WebExceptionHandler webExceptionHandler) {
        this.webExceptionHandler = webExceptionHandler;
        this.requestConverter = new DefaultSpringWebRequestConverter();
        this.responseConverter = new DefaultSpringWebResponseConverter();
    }

    public ResponseEntity<Object> handle(org.springframework.web.context.request.WebRequest request, Exception e) {
        WebRequest webRequest = this.requestConverter.convert(request);
        WebResponse response = this.webExceptionHandler.handle(webRequest, e);
        return this.responseConverter.convert(response);
    }

    public SpringWebRequestConverter getRequestConverter() {
        return this.requestConverter;
    }

    public void setRequestConverter(SpringWebRequestConverter requestConverter) {
        this.requestConverter = requestConverter;
    }

    public SpringWebResponseConverter getResponseConverter() {
        return this.responseConverter;
    }

    public void setResponseConverter(SpringWebResponseConverter responseConverter) {
        this.responseConverter = responseConverter;
    }
}
