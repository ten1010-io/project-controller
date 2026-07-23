package io.ten1010.common.jsonpatch;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.ten1010.common.jsonpatch.dto.JsonPatch;
import io.ten1010.common.jsonpatch.dto.JsonPatchOperation;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JsonPatchSerializer extends StdSerializer<JsonPatch> {

    public JsonPatchSerializer() {
        this(null);
    }

    public JsonPatchSerializer(@Nullable Class<JsonPatch> t) {
        super(t);
    }

    @Override
    public void serialize(JsonPatch value, JsonGenerator generator, SerializerProvider provider)
            throws IOException, JsonProcessingException {
        List<JsonPatchOperation> operations = value.getOperations();
        if (operations == null) {
            operations = new ArrayList<>();
        }

        generator.writeStartArray();
        for (JsonPatchOperation e : operations) {
            generator.writeObject(e);
        }
        generator.writeEndArray();
    }

}
