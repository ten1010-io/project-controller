package io.ten1010.common.eh;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public abstract class AbstractResponse implements Response {

    private final Exception exception;

}
