package io.ten1010.aipub.projectcontroller.domain.k8s;

import io.kubernetes.client.common.KubernetesObject;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public class K8sObjectTypeKey {

    public static K8sObjectTypeKey of(KubernetesObject object) {
        return new K8sObjectTypeKey(K8sObjectUtils.getApiVersion(object), K8sObjectUtils.getKind(object));
    }

    private final String apiVersion;
    private final String group;
    private final String version;
    private final String kind;

    public K8sObjectTypeKey(String apiVersion, String kind) {
        this.apiVersion = apiVersion;
        this.kind = kind;

        String[] tokens = apiVersion.split("/");
        if (tokens.length == 1) {
            this.group = "core";
            this.version = tokens[0];
        } else if (tokens.length == 2) {
            this.group = tokens[0];
            this.version = tokens[1];
        } else {
            throw new IllegalArgumentException();
        }
    }

    public K8sObjectTypeKey(String group, String version, String kind) {
        this.apiVersion = group + "/" + version;
        this.group = group;
        this.version = version;
        this.kind = kind;
    }

}
