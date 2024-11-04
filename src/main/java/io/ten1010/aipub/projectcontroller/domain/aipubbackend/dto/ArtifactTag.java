package io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class ArtifactTag {

    @Nullable
    private String id;
    @Nullable
    private String repositoryId;
    @Nullable
    private String artifactId;
    @Nullable
    private String name;
    @Nullable
    private Long pushTime;
    @Nullable
    private Long pullTime;
    @Nullable
    private Boolean immutable;

}
