package io.ten1010.common.eh.web.spring;

import io.ten1010.common.eh.web.WebRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;

public class SpringSecuritySpringWebRequestConverter implements SpringWebRequestConverter {
    private static String createAuthenticationDescription(org.springframework.web.context.request.WebRequest request) {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication()).map(Principal::getName).orElse("NotAuthenticated");
    }

    @Override
    public WebRequest convert(org.springframework.web.context.request.WebRequest request) {
        if (request instanceof ServletWebRequest) {
            ServletWebRequest servletWebRequest = (ServletWebRequest)request;
            HttpServletRequest servletRequest = servletWebRequest.getRequest();
            return new WebRequest(servletWebRequest.getHttpMethod().name(), servletRequest.getRequestURI(), servletRequest.getRemoteAddr(), SpringSecuritySpringWebRequestConverter.createAuthenticationDescription(request));
        }
        throw new IllegalArgumentException();
    }
}
