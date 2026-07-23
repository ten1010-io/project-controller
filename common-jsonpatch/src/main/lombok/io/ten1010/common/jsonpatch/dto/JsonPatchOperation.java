package io.ten1010.common.jsonpatch.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class JsonPatchOperation {

    @Nullable
    private String op;
    @Nullable
    private String path;
    @Nullable
    private JsonNode value;
    @Nullable
    private String from;

}
