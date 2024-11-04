package io.ten1010.aipub.projectcontroller.domain.k8s;

public class NamespaceNameResolver {

    public String resolveNamespaceName(String projectName) {
        return projectName;
    }

    public String resolveProjectName(String nsName) {
        return nsName;
    }

}
