package io.ten1010.common.eh.web;

import io.ten1010.common.eh.Request;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@AllArgsConstructor
@Getter
public class WebRequest implements Request {

    private final String method;
    private final String uri;
    private final String clientAddr;
    @Nullable
    private final String authentication;

}
