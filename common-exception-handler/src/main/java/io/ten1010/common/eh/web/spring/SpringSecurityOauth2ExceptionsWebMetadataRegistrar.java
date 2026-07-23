package io.ten1010.common.eh.web.spring;

import io.ten1010.common.eh.ExceptionMetadataRegistry;
import io.ten1010.common.eh.web.WebMetadataRegistrar;
import io.ten1010.common.eh.web.WebRequest;
import io.ten1010.common.eh.web.WebResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

public class SpringSecurityOauth2ExceptionsWebMetadataRegistrar implements WebMetadataRegistrar {
    @Override
    public void register(ExceptionMetadataRegistry<WebRequest, WebResponse> registry) {
        registry.register(OAuth2AuthenticationException.class, SpringExceptionMetadataEnum.OAuth2AuthenticationException.getMetadata());
    }
}
