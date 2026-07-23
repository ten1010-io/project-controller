package io.ten1010.common.jsonpatch;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class OperationError {

    private final String field;
    private final String reason;

}
