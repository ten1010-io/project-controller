package io.ten1010.common.jsonpatch.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.ten1010.common.jsonpatch.JsonPatchDeserializer;
import io.ten1010.common.jsonpatch.JsonPatchSerializer;
import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Data
@JsonSerialize(using = JsonPatchSerializer.class)
@JsonDeserialize(using = JsonPatchDeserializer.class)
public class JsonPatch {

    @Nullable
    private List<JsonPatchOperation> operations;

}
