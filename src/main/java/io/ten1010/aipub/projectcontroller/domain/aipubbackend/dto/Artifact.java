package io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Data
public class Artifact {

    @Nullable
    private String id;
    @Nullable
    private String namespaceId;
    @Nullable
    private String repositoryId;
    @Nullable
    private String type;
    @Nullable
    private String digest;
    @Nullable
    private Long size;
    @Nullable
    private Long pushTime;
    @Nullable
    private Long pullTime;
    @Nullable
    private List<ArtifactTag> tags;

}
