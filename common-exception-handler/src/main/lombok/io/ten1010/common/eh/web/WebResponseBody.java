package io.ten1010.common.eh.web;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@AllArgsConstructor
@Getter
public class WebResponseBody {

    private final String type;
    @Nullable
    private final Object detail;

}
