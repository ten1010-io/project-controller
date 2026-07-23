package io.ten1010.common.jsonpatch;

import com.fasterxml.jackson.databind.JsonNode;
import io.ten1010.common.jsonpatch.dto.JsonPatchOperation;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class JsonPatchOperationBuilder {

    public class AddOperationStep extends ValueOperationStep<AddOperationStep> {

        public AddOperationStep() {
            JsonPatchOperationBuilder.this.op = OpEnum.ADD.getStr();
        }

    }

    public class CopyOperationStep extends FromOperationStep<CopyOperationStep> {

        public CopyOperationStep() {
            JsonPatchOperationBuilder.this.op = OpEnum.COPY.getStr();
        }

    }

    public class MoveOperationStep extends FromOperationStep<MoveOperationStep> {

        public MoveOperationStep() {
            JsonPatchOperationBuilder.this.op = OpEnum.MOVE.getStr();
        }

    }

    public class RemoveOperationStep extends OperationStep<RemoveOperationStep> {

        public RemoveOperationStep() {
            JsonPatchOperationBuilder.this.op = OpEnum.REMOVE.getStr();
        }

    }

    public class ReplaceOperationStep extends ValueOperationStep<ReplaceOperationStep> {

        public ReplaceOperationStep() {
            JsonPatchOperationBuilder.this.op = OpEnum.REPLACE.getStr();
        }

    }

    public class TestOperationStep extends ValueOperationStep<TestOperationStep> {

        public TestOperationStep() {
            JsonPatchOperationBuilder.this.op = OpEnum.TEST.getStr();
        }

    }

    private abstract class OperationStep<T> {

        public T setPath(String path) {
            JsonPatchOperationBuilder.this.path = path;
            return (T) this;
        }

        public JsonPatchOperation build() {
            return JsonPatchOperationBuilder.this.build();
        }

    }

    private abstract class ValueOperationStep<T> extends OperationStep<T> {

        public T setValue(JsonNode value) {
            JsonPatchOperationBuilder.this.value = value;
            return (T) this;
        }

    }

    private abstract class FromOperationStep<T> extends OperationStep<T> {

        public T setFrom(String from) {
            JsonPatchOperationBuilder.this.from = from;
            return (T) this;
        }

    }

    private final JsonPatchValidator validator;

    @Nullable
    private String op;
    @Nullable
    private String path;
    @Nullable
    private JsonNode value;
    @Nullable
    private String from;

    public JsonPatchOperationBuilder() {
        this.validator = new JsonPatchValidator();
    }

    public JsonPatchOperation build() {
        JsonPatchOperation operation = new JsonPatchOperation();
        operation.setOp(this.op);
        operation.setPath(this.path);
        operation.setValue(this.value);
        operation.setFrom(this.from);

        List<OperationError> errors = this.validator.validate(operation);
        if (errors.isEmpty()) {
            return operation;
        }

        throw new InvalidPatchOperationException(errors);
    }

    public AddOperationStep add() {
        reset();
        return new AddOperationStep();
    }

    public CopyOperationStep copy() {
        reset();
        return new CopyOperationStep();
    }

    public MoveOperationStep move() {
        reset();
        return new MoveOperationStep();
    }

    public RemoveOperationStep remove() {
        reset();
        return new RemoveOperationStep();
    }

    public ReplaceOperationStep replace() {
        reset();
        return new ReplaceOperationStep();
    }

    public TestOperationStep test() {
        reset();
        return new TestOperationStep();
    }

    private void reset() {
        this.op = null;
        this.path = null;
        this.value = null;
        this.from = null;
    }

}
