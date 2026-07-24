package io.ten1010.common.jsonpatch;

import io.ten1010.common.jsonpatch.dto.JsonPatch;
import io.ten1010.common.jsonpatch.dto.JsonPatchOperation;

import java.util.ArrayList;
import java.util.List;

public class JsonPatchBuilder {

    private List<JsonPatchOperation> operations;
    private final JsonPatchValidator validator;

    public JsonPatchBuilder() {
        this.operations = new ArrayList<>();
        this.validator = new JsonPatchValidator();
    }

    public JsonPatch build() {
        JsonPatch patch = new JsonPatch();
        patch.setOperations(this.operations);

        PatchValidationResult result = this.validator.validate(patch);
        if (result.isErrorExist()) {
            throw new InvalidJsonPatchException(result);
        }

        return patch;
    }

    public JsonPatchBuilder withOperations(List<JsonPatchOperation> operations) {
        this.operations = operations;
        return this;
    }

    public JsonPatchBuilder addToOperations(JsonPatchOperation operation) {
        this.operations.add(operation);
        return this;
    }

}
