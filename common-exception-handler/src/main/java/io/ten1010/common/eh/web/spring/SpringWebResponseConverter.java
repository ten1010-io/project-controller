package io.ten1010.common.eh.web.spring;

import io.ten1010.common.eh.web.WebResponse;
import org.springframework.http.ResponseEntity;

public interface SpringWebResponseConverter {
    ResponseEntity<Object> convert(WebResponse response);
}
