package io.ten1010.common.jsonpatch;

import com.fasterxml.jackson.databind.node.NullNode;
import io.ten1010.common.jsonpatch.dto.JsonPatch;
import io.ten1010.common.jsonpatch.dto.JsonPatchOperation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonPatchValidatorTest {

    JsonPatchValidator validator = new JsonPatchValidator();

    @Test
    void given_null_op_operation() {
        JsonPatchOperation operation = new JsonPatchOperation();
        operation.setOp(null);
        operation.setPath("/foo");

        List<OperationError> errors = this.validator.validate(operation);

        assertEquals(1, errors.size());

        OperationError error = errors.get(0);
        assertEquals("op", error.getField());
    }

    @Test
    void given_invalid_op_operation() {
        JsonPatchOperation operation = new JsonPatchOperation();
        operation.setOp("foo");
        operation.setPath("/foo");

        List<OperationError> errors = this.validator.validate(operation);

        assertEquals(1, errors.size());

        OperationError error = errors.get(0);
        assertEquals("op", error.getField());
    }

    @Test
    void given_valid_remove_operation() {
        JsonPatchOperation operation = new JsonPatchOperation();
        operation.setOp("remove");
        operation.setPath("/foo");

        List<OperationError> errors = this.validator.validate(operation);

        assertEquals(0, errors.size());
    }

    @Test
    void given_valid_add_operation() {
        JsonPatchOperation operation = new JsonPatchOperation();
        operation.setOp("add");
        operation.setPath("/foo");
        operation.setValue(NullNode.getInstance());

        List<OperationError> errors = this.validator.validate(operation);

        assertEquals(0, errors.size());
    }

    @Test
    void given_invalid_path_add_operation() {
        JsonPatchOperation operation = new JsonPatchOperation();
        operation.setOp("add");
        operation.setPath("foo");
        operation.setValue(NullNode.getInstance());

        List<OperationError> errors = this.validator.validate(operation);

        assertEquals(1, errors.size());

        OperationError error = errors.get(0);
        assertEquals("path", error.getField());
    }

    @Test
    void given_invalid_value_add_operation() {
        JsonPatchOperation operation = new JsonPatchOperation();
        operation.setOp("add");
        operation.setPath("/foo");
        operation.setValue(null);

        List<OperationError> errors = this.validator.validate(operation);

        assertEquals(1, errors.size());

        OperationError error = errors.get(0);
        assertEquals("value", error.getField());
    }

    @Test
    void given_add_operation_that_has_non_null_from() {
        JsonPatchOperation operation = new JsonPatchOperation();
        operation.setOp("add");
        operation.setPath("/foo");
        operation.setValue(NullNode.getInstance());
        operation.setFrom("foo");

        List<OperationError> errors = this.validator.validate(operation);

        assertEquals(1, errors.size());

        OperationError error = errors.get(0);
        assertEquals("from", error.getField());
    }

    @Test
    void given_invalid_fields_add_operation() {
        JsonPatchOperation operation = new JsonPatchOperation();
        operation.setOp("add");
        operation.setPath("foo");
        operation.setValue(null);

        List<OperationError> errors = this.validator.validate(operation);

        assertEquals(2, errors.size());

        OperationError error1 = errors.get(0);
        assertEquals("path", error1.getField());

        OperationError error2 = errors.get(1);
        assertEquals("value", error2.getField());
    }

    @Test
    void given_valid_copy_operation() {
        JsonPatchOperation operation = new JsonPatchOperation();
        operation.setOp("copy");
        operation.setPath("/foo");
        operation.setFrom("/bar");

        List<OperationError> errors = this.validator.validate(operation);

        assertEquals(0, errors.size());
    }

    @Test
    void given_invalid_path_copy_operation() {
        JsonPatchOperation operation = new JsonPatchOperation();
        operation.setOp("copy");
        operation.setPath("foo");
        operation.setFrom("/bar");

        List<OperationError> errors = this.validator.validate(operation);

        assertEquals(1, errors.size());

        OperationError error = errors.get(0);
        assertEquals("path", error.getField());
    }

    @Test
    void given_invalid_from_copy_operation() {
        JsonPatchOperation operation = new JsonPatchOperation();
        operation.setOp("copy");
        operation.setPath("/foo");
        operation.setFrom("bar");

        List<OperationError> errors = this.validator.validate(operation);

        assertEquals(1, errors.size());

        OperationError error = errors.get(0);
        assertEquals("from", error.getField());
    }

    @Test
    void given_copy_operation_that_has_non_null_value() {
        JsonPatchOperation operation = new JsonPatchOperation();
        operation.setOp("copy");
        operation.setPath("/foo");
        operation.setFrom("/bar");
        operation.setValue(NullNode.getInstance());

        List<OperationError> errors = this.validator.validate(operation);

        assertEquals(1, errors.size());

        OperationError error = errors.get(0);
        assertEquals("value", error.getField());
    }

    @Test
    void given_invalid_fields_copy_operation() {
        JsonPatchOperation operation = new JsonPatchOperation();
        operation.setOp("copy");
        operation.setPath("foo");
        operation.setFrom("bar");

        List<OperationError> errors = this.validator.validate(operation);

        assertEquals(2, errors.size());

        OperationError error1 = errors.get(0);
        assertEquals("path", error1.getField());

        OperationError error2 = errors.get(1);
        assertEquals("from", error2.getField());
    }

    @Test
    void given_null_operations() {
        JsonPatch jsonPatch = new JsonPatch();

        PatchValidationResult result = this.validator.validate(jsonPatch);
        assertTrue(result.isErrorExist());
        assertEquals(0, result.getErrors().size());
    }

    @Test
    void given_valid_operations() {
        JsonPatchOperation op1 = new JsonPatchOperation();
        op1.setOp("remove");
        op1.setPath("/foo");
        JsonPatchOperation op2 = new JsonPatchOperation();
        op2.setOp("remove");
        op2.setPath("/bar");

        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.setOperations(List.of(op1, op2));

        PatchValidationResult result = this.validator.validate(jsonPatch);
        assertFalse(result.isErrorExist());
    }

    @Test
    void given_invalid_operations() {
        JsonPatchOperation op1 = new JsonPatchOperation();
        op1.setOp("remove");
        op1.setPath("foo");
        JsonPatchOperation op2 = new JsonPatchOperation();
        op2.setOp("remove");
        op2.setPath("/bar");

        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.setOperations(List.of(op1, op2));

        PatchValidationResult result = this.validator.validate(jsonPatch);
        assertTrue(result.isErrorExist());

        List<OperationsError> operationsErrors = result.getErrors();
        assertEquals(1, operationsErrors.size());

        OperationsError error = operationsErrors.get(0);
        assertEquals(0, error.getIndex());
        assertEquals(1, error.getErrors().size());
        assertEquals("path", error.getErrors().get(0).getField());
    }

}