package io.ten1010.common.jsonpatch;

import lombok.Getter;

@Getter
public class InvalidJsonPatchException extends RuntimeException {

    private final PatchValidationResult result;

    public InvalidJsonPatchException(PatchValidationResult result) {
        super(result.toString());
        this.result = result;
    }

}
