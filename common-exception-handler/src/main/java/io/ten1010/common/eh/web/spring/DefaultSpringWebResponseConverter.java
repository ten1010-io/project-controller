package io.ten1010.common.eh.web.spring;

import io.ten1010.common.eh.web.WebResponse;
import org.springframework.http.ResponseEntity;

public class DefaultSpringWebResponseConverter implements SpringWebResponseConverter {
    @Override
    public ResponseEntity<Object> convert(WebResponse response) {
        return ResponseEntity.status(response.getHttpStatus()).body(response.getBody());
    }
}
