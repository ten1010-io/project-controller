package io.ten1010.aipub.projectcontroller.domain.k8s;

public class KeyResolver {

    private static final String DELIMITER = "/";

    public String resolveKey(String objName) {
        return objName;
    }

    public String resolveKey(String objNamespace, String objName) {
        return objNamespace + DELIMITER + objName;
    }

}
