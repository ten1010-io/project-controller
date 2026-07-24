package io.ten1010.common.eh.web.spring;

import io.ten1010.common.eh.ExceptionMetadataRegistry;
import io.ten1010.common.eh.web.WebMetadataRegistrar;
import io.ten1010.common.eh.web.WebRequest;
import io.ten1010.common.eh.web.WebResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class SpringSecurityExceptionsWebMetadataRegistrar implements WebMetadataRegistrar {
    @Override
    public void register(ExceptionMetadataRegistry<WebRequest, WebResponse> registry) {
        registry.register(AuthenticationException.class, SpringExceptionMetadataEnum.AuthenticationException.getMetadata())
                .register(BadCredentialsException.class, SpringExceptionMetadataEnum.BadCredentialsException.getMetadata())
                .register(InsufficientAuthenticationException.class, SpringExceptionMetadataEnum.InsufficientAuthenticationException.getMetadata())
                .register(UsernameNotFoundException.class, SpringExceptionMetadataEnum.UsernameNotFoundException.getMetadata())
                .register(AccessDeniedException.class, SpringExceptionMetadataEnum.AccessDeniedException.getMetadata());
    }
}
