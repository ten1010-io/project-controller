package io.ten1010.common.eh.web.spring;

import io.ten1010.common.eh.web.WebRequest;

public interface SpringWebRequestConverter {
    WebRequest convert(org.springframework.web.context.request.WebRequest request);
}
