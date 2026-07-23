package io.ten1010.common.jsonpatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ten1010.common.jsonpatch.dto.JsonPatch;
import io.ten1010.common.jsonpatch.dto.JsonPatchOperation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonPatchDeserializationTest {

    ObjectMapper mapper = new ObjectMapper();

    @Test
    void given_empty_json() {
        String json = "";

        assertThrows(Exception.class, () -> {
            this.mapper.readValue(json, JsonPatch.class);
        });
    }

    @Test
    void given_object_json() {
        String json = "{}";

        assertThrows(Exception.class, () -> {
            this.mapper.readValue(json, JsonPatch.class);
        });
    }

    @Test
    void given_empty_array_json() throws JsonProcessingException {
        String json = "[]";
        JsonPatch expected = new JsonPatch();
        expected.setOperations(List.of());

        JsonPatch jsonPatch = this.mapper.readValue(json, JsonPatch.class);

        assertEquals(expected, jsonPatch);
    }

    @Test
    void given_operation_array_json() throws JsonProcessingException {
        String json = "[" +
                "{\"op\": \"remove\", \"path\": \"/foo\"}" +
                ", " +
                "{\"op\": \"remove\", \"path\": \"/bar\"}" +
                "]";

        JsonPatchOperation op1 = new JsonPatchOperation();
        op1.setOp("remove");
        op1.setPath("/foo");
        JsonPatchOperation op2 = new JsonPatchOperation();
        op2.setOp("remove");
        op2.setPath("/bar");

        JsonPatch expected = new JsonPatch();
        expected.setOperations(List.of(op1, op2));

        JsonPatch jsonPatch = this.mapper.readValue(json, JsonPatch.class);

        assertEquals(expected, jsonPatch);
    }

}