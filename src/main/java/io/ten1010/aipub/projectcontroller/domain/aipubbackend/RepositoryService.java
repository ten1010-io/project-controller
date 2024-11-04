package io.ten1010.aipub.projectcontroller.domain.aipubbackend;

import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.Repository;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.RepositoryListOptions;

import java.util.List;

public interface RepositoryService {

    List<Repository> listNamespacedRepositories(String namespacedId, RepositoryListOptions options);

}
