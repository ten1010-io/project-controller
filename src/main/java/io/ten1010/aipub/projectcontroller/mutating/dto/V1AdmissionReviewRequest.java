package io.ten1010.aipub.projectcontroller.mutating.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1AdmissionReviewRequest {

    @Nullable
    private String uid;
    @Nullable
    private V1Kind kind;
    @Nullable
    private V1Resource resource;
    @Nullable
    private String name;
    @Nullable
    private String namespace;
    @Nullable
    private String operation;
    @Nullable
    private V1UserInfo userInfo;
    @Nullable
    private JsonNode object;

}
