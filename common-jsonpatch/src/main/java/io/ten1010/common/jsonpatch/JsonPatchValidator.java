package io.ten1010.common.jsonpatch;

import com.fasterxml.jackson.core.JsonPointer;
import io.ten1010.common.jsonpatch.dto.JsonPatch;
import io.ten1010.common.jsonpatch.dto.JsonPatchOperation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JsonPatchValidator {

    private static final OperationError INVALID_OP = new OperationError("op", "Invalid");
    private static final OperationError INVALID_VALUE = new OperationError("value", "Invalid");

    private static final OperationError CAN_NOT_BE_SET_VALUE = new OperationError("value", "Can not be set");
    private static final OperationError CAN_NOT_BE_SET_FROM = new OperationError("from", "Can not be set");

    public PatchValidationResult validate(JsonPatch patch) {
        List<JsonPatchOperation> operations = patch.getOperations();
        if (operations == null) {
            return new PatchValidationResult(true, "Field[operations] can not be null", List.of());
        }

        List<OperationsError> errors = new ArrayList<>();
        for (int i = 0; i < operations.size(); i++) {
            JsonPatchOperation operation = operations.get(i);
            List<OperationError> operationErrors = validate(operation);
            if (!operationErrors.isEmpty()) {
                errors.add(new OperationsError(i, operationErrors));
            }
        }
        if (errors.isEmpty()) {
            return new PatchValidationResult(false, null, List.of());
        }

        return new PatchValidationResult(true, "Operation which has error exists", errors);
    }

    public List<OperationError> validate(JsonPatchOperation operation) {
        List<OperationError> errors = new ArrayList<>();

        String op = operation.getOp();
        if (op == null) {
            errors.add(INVALID_OP);
            return errors;
        }

        Optional<OpEnum> opEnumOpt = OpEnum.getEnum(op);
        if (opEnumOpt.isEmpty()) {
            errors.add(INVALID_OP);
            return errors;
        }

        Optional<OperationError> pathErrOpt = validatePath("path", operation.getPath());
        pathErrOpt.ifPresent(errors::add);

        return switch (opEnumOpt.get()) {
            case REMOVE -> {
                if (operation.getValue() != null) {
                    errors.add(CAN_NOT_BE_SET_VALUE);
                }
                if (operation.getFrom() != null) {
                    errors.add(CAN_NOT_BE_SET_FROM);
                }
                yield errors;
            }
            case ADD, REPLACE, TEST -> {
                if (operation.getValue() == null) {
                    errors.add(INVALID_VALUE);
                }
                if (operation.getFrom() != null) {
                    errors.add(CAN_NOT_BE_SET_FROM);
                }
                yield errors;
            }
            case COPY, MOVE -> {
                Optional<OperationError> fromErrOpt = validatePath("from", operation.getFrom());
                fromErrOpt.ifPresent(errors::add);
                if (operation.getValue() != null) {
                    errors.add(CAN_NOT_BE_SET_VALUE);
                }
                yield errors;
            }
        };
    }

    private Optional<OperationError> validatePath(String field, @Nullable String path) {
        try {
            JsonPointer.valueOf(path);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(new OperationError(field, "Invalid path"));
        }
    }

}
