package io.ten1010.common.jsonpatch;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.List;

@AllArgsConstructor
@Getter
public class PatchValidationResult {

    private final boolean errorExist;
    @Nullable
    private final String reason;
    private final List<OperationsError> errors;

}
