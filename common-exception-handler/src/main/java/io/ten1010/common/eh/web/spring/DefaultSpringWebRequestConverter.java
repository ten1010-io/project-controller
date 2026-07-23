package io.ten1010.common.eh.web.spring;

import io.ten1010.common.eh.web.WebRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

public class DefaultSpringWebRequestConverter implements SpringWebRequestConverter {
    @Override
    public WebRequest convert(org.springframework.web.context.request.WebRequest request) {
        if (request instanceof ServletWebRequest) {
            ServletWebRequest servletWebRequest = (ServletWebRequest)request;
            HttpServletRequest servletRequest = servletWebRequest.getRequest();
            return new WebRequest(servletWebRequest.getHttpMethod().name(), servletRequest.getRequestURI(), servletRequest.getRemoteAddr(), null);
        }
        throw new IllegalArgumentException();
    }
}
