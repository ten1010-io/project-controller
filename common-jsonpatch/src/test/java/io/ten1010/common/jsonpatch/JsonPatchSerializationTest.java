package io.ten1010.common.jsonpatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ten1010.common.jsonpatch.dto.JsonPatch;
import io.ten1010.common.jsonpatch.dto.JsonPatchOperation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonPatchSerializationTest {

    ObjectMapper mapper = new ObjectMapper();

    @Test
    void given_empty_JsonPatch() throws JsonProcessingException {
        JsonPatch jsonPatch1 = new JsonPatch();
        JsonPatch jsonPatch2 = new JsonPatch();
        jsonPatch2.setOperations(List.of());

        String expected = "[]";

        String serialized1 = this.mapper.writeValueAsString(jsonPatch1);
        String serialized2 = this.mapper.writeValueAsString(jsonPatch2);

        assertEquals(expected, serialized1);
        assertEquals(expected, serialized2);
    }

    @Test
    void given_JsonPatch_that_has_operations() throws JsonProcessingException {
        JsonPatchOperation op1 = new JsonPatchOperation();
        op1.setOp("remove");
        op1.setPath("/foo");
        JsonPatchOperation op2 = new JsonPatchOperation();
        op2.setOp("remove");
        op2.setPath("/bar");
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.setOperations(List.of(op1, op2));

        String expected = "[" +
                "{\"op\":\"remove\",\"path\":\"/foo\",\"value\":null,\"from\":null}" +
                "," +
                "{\"op\":\"remove\",\"path\":\"/bar\",\"value\":null,\"from\":null}" +
                "]";

        String serialized = this.mapper.writeValueAsString(jsonPatch);

        assertEquals(expected, serialized);
    }

}