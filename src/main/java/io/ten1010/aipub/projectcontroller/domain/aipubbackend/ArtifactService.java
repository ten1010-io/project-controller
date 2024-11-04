package io.ten1010.aipub.projectcontroller.domain.aipubbackend;

import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.Artifact;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ArtifactListOptions;

import java.util.List;

public interface ArtifactService {

    List<Artifact> listArtifacts(String namespacedId, String repositoryName, ArtifactListOptions options);

}
