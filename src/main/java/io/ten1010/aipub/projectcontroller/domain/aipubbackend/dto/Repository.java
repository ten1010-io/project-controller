package io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class Repository {

    @Nullable
    private String id;
    @Nullable
    private String namespaceId;
    @Nullable
    private String name;
    @Nullable
    private Long createdTimestamp;
    @Nullable
    private Long artifactCount;
    @Nullable
    private Long pullCount;

}
