package io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl;

import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ArtifactListOptions;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobotListOptions;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ListOptions;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.RepositoryListOptions;

import java.util.Map;

public abstract class ListOptionsUtils {

    public static void applyListOptions(Map<String, String> queryParams, ListOptions options) {
        if (options.getPageOffset() != null) {
            queryParams.put("pageOffset", String.valueOf(options.getPageOffset()));
        }
        if (options.getPageSize() != null) {
            queryParams.put("pageSize", String.valueOf(options.getPageSize()));
        }
    }

    public static void applyImageRegistryRobotListOptions(Map<String, String> queryParams, ImageRegistryRobotListOptions options) {
        applyListOptions(queryParams, options);
    }

    public static void applyRepositoryListOptions(Map<String, String> queryParams, RepositoryListOptions options) {
        applyListOptions(queryParams, options);
    }

    public static void applyArtifactListOptions(Map<String, String> queryParams, ArtifactListOptions options) {
        applyListOptions(queryParams, options);
    }

}
