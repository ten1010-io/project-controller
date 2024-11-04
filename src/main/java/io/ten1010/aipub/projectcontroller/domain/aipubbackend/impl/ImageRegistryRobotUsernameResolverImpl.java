package io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl;

import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ImageRegistryRobotUsernameResolver;

public class ImageRegistryRobotUsernameResolverImpl implements ImageRegistryRobotUsernameResolver {

    private static final String PREFIX = "robot$project-aipub-ten1010-io-";

    @Override
    public String resolve(String projectName) {
        return PREFIX + projectName;
    }

}
