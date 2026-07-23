package io.ten1010.common.jsonpatch;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
@Getter
public class OperationsError {

    private final int index;
    private final List<OperationError> errors;

}
