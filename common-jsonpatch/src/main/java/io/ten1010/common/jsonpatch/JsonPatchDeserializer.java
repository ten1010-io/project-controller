package io.ten1010.common.jsonpatch;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.ten1010.common.jsonpatch.dto.JsonPatch;
import io.ten1010.common.jsonpatch.dto.JsonPatchOperation;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;

public class JsonPatchDeserializer extends StdDeserializer<JsonPatch> {

    private static final TypeReference<List<JsonPatchOperation>> TYPE_REFERENCE = new TypeReference<>() {
    };

    public JsonPatchDeserializer() {
        this(null);
    }

    public JsonPatchDeserializer(@Nullable Class<?> vc) {
        super(vc);
    }

    @Override
    public JsonPatch deserialize(JsonParser jp, DeserializationContext ctx) throws IOException, JsonProcessingException {
        List<JsonPatchOperation> operations = jp.readValueAs(TYPE_REFERENCE);

        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.setOperations(operations);

        return jsonPatch;
    }

}
