package io.ten1010.common.eh.web.spring;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class Oauth2ErrorDetail {

    @Nullable
    private String errorCode;
    @Nullable
    private String description;
    @Nullable
    private String uri;

}
