package io.ten1010.common.jsonpatch;

import com.fasterxml.jackson.databind.node.NullNode;
import io.ten1010.common.jsonpatch.dto.JsonPatchOperation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonPatchOperationBuilderTest {

    @Test
    void given_add_operation() {
        JsonPatchOperation expected = new JsonPatchOperation();
        expected.setOp("add");
        expected.setPath("/foo");
        expected.setValue(NullNode.getInstance());

        JsonPatchOperationBuilder builder = new JsonPatchOperationBuilder();
        JsonPatchOperation addOperation = builder.add()
                .setPath("/foo")
                .setValue(NullNode.getInstance())
                .build();

        assertEquals(expected, addOperation);
    }

    @Test
    void given_copy_operation() {
        JsonPatchOperation expected = new JsonPatchOperation();
        expected.setOp("copy");
        expected.setPath("/foo");
        expected.setFrom("/bar");

        JsonPatchOperationBuilder builder = new JsonPatchOperationBuilder();
        JsonPatchOperation operation = builder.copy()
                .setPath("/foo")
                .setFrom("/bar")
                .build();

        assertEquals(expected, operation);
    }

    @Test
    void given_move_operation() {
        JsonPatchOperation expected = new JsonPatchOperation();
        expected.setOp("move");
        expected.setPath("/foo");
        expected.setFrom("/bar");

        JsonPatchOperationBuilder builder = new JsonPatchOperationBuilder();
        JsonPatchOperation operation = builder.move()
                .setPath("/foo")
                .setFrom("/bar")
                .build();

        assertEquals(expected, operation);
    }

    @Test
    void given_remove_operation() {
        JsonPatchOperation expected = new JsonPatchOperation();
        expected.setOp("remove");
        expected.setPath("/foo");

        JsonPatchOperationBuilder builder = new JsonPatchOperationBuilder();
        JsonPatchOperation operation = builder.remove()
                .setPath("/foo")
                .build();

        assertEquals(expected, operation);
    }

    @Test
    void given_replace_operation() {
        JsonPatchOperation expected = new JsonPatchOperation();
        expected.setOp("replace");
        expected.setPath("/foo");
        expected.setValue(NullNode.getInstance());

        JsonPatchOperationBuilder builder = new JsonPatchOperationBuilder();
        JsonPatchOperation operation = builder.replace()
                .setPath("/foo")
                .setValue(NullNode.getInstance())
                .build();

        assertEquals(expected, operation);
    }

    @Test
    void given_test_operation() {
        JsonPatchOperation expected = new JsonPatchOperation();
        expected.setOp("test");
        expected.setPath("/foo");
        expected.setValue(NullNode.getInstance());

        JsonPatchOperationBuilder builder = new JsonPatchOperationBuilder();
        JsonPatchOperation operation = builder.test()
                .setPath("/foo")
                .setValue(NullNode.getInstance())
                .build();

        assertEquals(expected, operation);
    }

}