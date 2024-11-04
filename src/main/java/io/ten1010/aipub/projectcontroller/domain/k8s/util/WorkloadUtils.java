package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import io.kubernetes.client.openapi.models.*;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public abstract class WorkloadUtils {

    public static Optional<String> getNodeName(V1Pod object) {
        Objects.requireNonNull(object.getSpec());
        return Optional.ofNullable(object.getSpec().getNodeName());
    }

    public static List<V1Toleration> getTolerations(V1Pod pod) {
        if (pod.getSpec() == null) {
            return List.of();
        }
        if (pod.getSpec().getTolerations() == null) {
            return List.of();
        }
        return pod.getSpec().getTolerations();
    }

    public static List<V1Toleration> getTolerations(V1PodTemplateSpec podTemplateSpec) {
        if (podTemplateSpec.getSpec() == null) {
            return List.of();
        }
        if (podTemplateSpec.getSpec().getTolerations() == null) {
            return List.of();
        }
        return podTemplateSpec.getSpec().getTolerations();
    }

    public static List<V1NodeSelectorTerm> getNodeSelectorTerms(V1Pod pod) {
        if (pod.getSpec() == null ||
                pod.getSpec().getAffinity() == null ||
                pod.getSpec().getAffinity().getNodeAffinity() == null ||
                pod.getSpec().getAffinity().getNodeAffinity().getRequiredDuringSchedulingIgnoredDuringExecution() == null) {
            return List.of();
        }
        return pod.getSpec().getAffinity().getNodeAffinity().getRequiredDuringSchedulingIgnoredDuringExecution().getNodeSelectorTerms();
    }

    public static List<V1NodeSelectorTerm> getNodeSelectorTerms(V1PodTemplateSpec podTemplateSpec) {
        if (podTemplateSpec.getSpec() == null ||
                podTemplateSpec.getSpec().getAffinity() == null ||
                podTemplateSpec.getSpec().getAffinity().getNodeAffinity() == null ||
                podTemplateSpec.getSpec().getAffinity().getNodeAffinity().getRequiredDuringSchedulingIgnoredDuringExecution() == null) {
            return List.of();
        }
        return podTemplateSpec.getSpec().getAffinity().getNodeAffinity().getRequiredDuringSchedulingIgnoredDuringExecution().getNodeSelectorTerms();
    }

    public static List<V1LocalObjectReference> getImagePullSecrets(V1Pod pod) {
        if (pod.getSpec() == null ||
                pod.getSpec().getImagePullSecrets() == null) {
            return List.of();
        }
        return pod.getSpec().getImagePullSecrets();
    }

    public static List<V1LocalObjectReference> getImagePullSecrets(V1PodTemplateSpec podTemplateSpec) {
        if (podTemplateSpec.getSpec() == null ||
                podTemplateSpec.getSpec().getImagePullSecrets() == null) {
            return List.of();
        }
        return podTemplateSpec.getSpec().getImagePullSecrets();
    }

    public static V1PodTemplateSpec getPodTemplateSpec(V1CronJob object) {
        Objects.requireNonNull(object.getSpec());
        Objects.requireNonNull(object.getSpec().getJobTemplate().getSpec());
        return object.getSpec().getJobTemplate().getSpec().getTemplate();
    }

    public static List<V1Toleration> getTolerations(V1CronJob object) {
        V1PodTemplateSpec templateSpec = getPodTemplateSpec(object);
        return getTolerations(templateSpec);
    }

    public static List<V1NodeSelectorTerm> getNodeSelectorTerms(V1CronJob object) {
        V1PodTemplateSpec templateSpec = getPodTemplateSpec(object);
        return getNodeSelectorTerms(templateSpec);
    }

    public static List<V1LocalObjectReference> getImagePullSecrets(V1CronJob object) {
        V1PodTemplateSpec templateSpec = getPodTemplateSpec(object);
        return getImagePullSecrets(templateSpec);
    }

    public static V1PodTemplateSpec getPodTemplateSpec(V1DaemonSet object) {
        Objects.requireNonNull(object.getSpec());
        return object.getSpec().getTemplate();
    }

    public static List<V1Toleration> getTolerations(V1DaemonSet object) {
        V1PodTemplateSpec templateSpec = getPodTemplateSpec(object);
        return getTolerations(templateSpec);
    }

    public static List<V1NodeSelectorTerm> getNodeSelectorTerms(V1DaemonSet object) {
        V1PodTemplateSpec templateSpec = getPodTemplateSpec(object);
        return getNodeSelectorTerms(templateSpec);
    }

    public static List<V1LocalObjectReference> getImagePullSecrets(V1DaemonSet object) {
        V1PodTemplateSpec templateSpec = getPodTemplateSpec(object);
        return getImagePullSecrets(templateSpec);
    }

    public static V1PodTemplateSpec getPodTemplateSpec(V1Deployment object) {
        Objects.requireNonNull(object.getSpec());
        return object.getSpec().getTemplate();
    }

    public static List<V1Toleration> getTolerations(V1Deployment object) {
        V1PodTemplateSpec templateSpec = getPodTemplateSpec(object);
        return getTolerations(templateSpec);
    }

    public static List<V1NodeSelectorTerm> getNodeSelectorTerms(V1Deployment object) {
        V1PodTemplateSpec templateSpec = getPodTemplateSpec(object);
        return getNodeSelectorTerms(templateSpec);
    }

    public static List<V1LocalObjectReference> getImagePullSecrets(V1Deployment object) {
        V1PodTemplateSpec templateSpec = getPodTemplateSpec(object);
        return getImagePullSecrets(templateSpec);
    }

    public static V1PodTemplateSpec getPodTemplateSpec(V1Job object) {
        Objects.requireNonNull(object.getSpec());
        return object.getSpec().getTemplate();
    }

    public static List<V1Toleration> getTolerations(V1Job object) {
        V1PodTemplateSpec templateSpec = getPodTemplateSpec(object);
        return getTolerations(templateSpec);
    }

    public static List<V1NodeSelectorTerm> getNodeSelectorTerms(V1Job object) {
        V1PodTemplateSpec templateSpec = getPodTemplateSpec(object);
        return getNodeSelectorTerms(templateSpec);
    }

    public static List<V1LocalObjectReference> getImagePullSecrets(V1Job object) {
        V1PodTemplateSpec templateSpec = getPodTemplateSpec(object);
        return getImagePullSecrets(templateSpec);
    }

    public static V1PodTemplateSpec getPodTemplateSpec(V1ReplicaSet object) {
        Objects.requireNonNull(object.getSpec());
        Objects.requireNonNull(object.getSpec().getTemplate());
        return object.getSpec().getTemplate();
    }

    public static List<V1Toleration> getTolerations(V1ReplicaSet object) {
        V1PodTemplateSpec templateSpec = getPodTemplateSpec(object);
        return getTolerations(templateSpec);
    }

    public static List<V1NodeSelectorTerm> getNodeSelectorTerms(V1ReplicaSet object) {
        V1PodTemplateSpec templateSpec = getPodTemplateSpec(object);
        return getNodeSelectorTerms(templateSpec);
    }

    public static List<V1LocalObjectReference> getImagePullSecrets(V1ReplicaSet object) {
        V1PodTemplateSpec templateSpec = getPodTemplateSpec(object);
        return getImagePullSecrets(templateSpec);
    }

    public static V1PodTemplateSpec getPodTemplateSpec(V1StatefulSet object) {
        Objects.requireNonNull(object.getSpec());
        return object.getSpec().getTemplate();
    }

    public static List<V1Toleration> getTolerations(V1StatefulSet object) {
        V1PodTemplateSpec templateSpec = getPodTemplateSpec(object);
        return getTolerations(templateSpec);
    }

    public static List<V1NodeSelectorTerm> getNodeSelectorTerms(V1StatefulSet object) {
        V1PodTemplateSpec templateSpec = getPodTemplateSpec(object);
        return getNodeSelectorTerms(templateSpec);
    }

    public static List<V1LocalObjectReference> getImagePullSecrets(V1StatefulSet object) {
        V1PodTemplateSpec templateSpec = getPodTemplateSpec(object);
        return getImagePullSecrets(templateSpec);
    }

    public static List<V1NodeSelectorRequirement> getMatchExpressions(V1NodeSelectorTerm nodeSelectorTerm) {
        if (nodeSelectorTerm.getMatchExpressions() == null) {
            return List.of();
        }
        return nodeSelectorTerm.getMatchExpressions();
    }

    public static List<V1NodeSelectorRequirement> getMatchFields(V1NodeSelectorTerm nodeSelectorTerm) {
        if (nodeSelectorTerm.getMatchFields() == null) {
            return List.of();
        }
        return nodeSelectorTerm.getMatchFields();
    }

}
