package io.ten1010.common.jsonpatch;

import io.ten1010.common.jsonpatch.dto.JsonPatch;
import io.ten1010.common.jsonpatch.dto.JsonPatchOperation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonPatchBuilderTest {

    JsonPatchBuilder builder = new JsonPatchBuilder();

    @Test
    void given_valid_operations() {
        JsonPatchOperation op1 = new JsonPatchOperation();
        op1.setOp("remove");
        op1.setPath("/foo");
        JsonPatchOperation op2 = new JsonPatchOperation();
        op2.setOp("remove");
        op2.setPath("/bar");

        JsonPatch patch1 = this.builder
                .addToOperations(op1)
                .addToOperations(op2)
                .build();
        JsonPatch patch2 = this.builder
                .withOperations(List.of(op1, op2))
                .build();

        assertEquals(patch1, patch2);
    }

    @Test
    void given_invalid_operations() {
        JsonPatchOperation op1 = new JsonPatchOperation();
        op1.setOp("remove");
        op1.setPath("/foo");
        JsonPatchOperation op2 = new JsonPatchOperation();
        op2.setOp("remove");
        op2.setPath("bar");

        try {
            this.builder
                    .addToOperations(op1)
                    .addToOperations(op2)
                    .build();
            fail();
        } catch (Exception e) {
            assertInstanceOf(InvalidJsonPatchException.class, e);

            InvalidJsonPatchException ex = (InvalidJsonPatchException) e;
            PatchValidationResult result = ex.getResult();
            assertTrue(result.isErrorExist());

            List<OperationsError> operationsErrors = result.getErrors();
            assertEquals(1, operationsErrors.size());
            assertEquals(1, operationsErrors.get(0).getIndex());

            List<OperationError> errors = operationsErrors.get(0).getErrors();
            assertEquals(1, errors.size());
            assertEquals("path", errors.get(0).getField());
        }
    }

}