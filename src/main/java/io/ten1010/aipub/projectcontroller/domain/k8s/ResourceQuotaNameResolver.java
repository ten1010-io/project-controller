package io.ten1010.aipub.projectcontroller.domain.k8s;

import java.util.Optional;

public class ResourceQuotaNameResolver {

    private static final String PREFIX = "project-aipub-ten1010-io-";

    public String resolveQuotaName(String projectName) {
        return PREFIX + projectName;
    }

    public Optional<String> resolveProjectName(String quotaName) {
        if (!quotaName.startsWith(PREFIX)) {
            return Optional.empty();
        }

        return Optional.of(quotaName.substring(PREFIX.length()));
    }

}
