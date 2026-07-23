package io.ten1010.common.jsonpatch;

import lombok.Getter;

import java.util.List;

@Getter
public class InvalidPatchOperationException extends RuntimeException {

    private final List<OperationError> errors;

    public InvalidPatchOperationException(List<OperationError> errors) {
        super(errors.toString());
        this.errors = errors;
    }

}
