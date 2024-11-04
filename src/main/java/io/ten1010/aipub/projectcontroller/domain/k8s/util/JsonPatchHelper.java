package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ten1010.common.jsonpatch.dto.JsonPatch;
import lombok.AllArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@AllArgsConstructor
public class JsonPatchHelper {

    private final ObjectMapper mapper;

    public String buildPatchString(JsonPatch jsonPatch) {
        try {
            String json = this.mapper.writeValueAsString(jsonPatch);
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
            return Base64.getEncoder().encodeToString(jsonBytes);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
