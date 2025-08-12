package io.ten1010.aipub.projectcontroller.domain.k8s;

import io.kubernetes.client.openapi.models.*;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.*;

public final class K8sObjectTypeConstants {

    public static final K8sObjectType<V1alpha1Project> PROJECT_V1ALPHA1 = new K8sObjectType<>(
            new K8sObjectTypeKey(
                    ProjectApiConstants.PROJECT_GROUP,
                    ProjectApiConstants.VERSION,
                    ProjectApiConstants.PROJECT_RESOURCE_KIND),
            V1alpha1Project.class);
    public static final K8sObjectType<V1alpha1NodeGroup> NODE_GROUP_V1ALPHA1 = new K8sObjectType<>(
            new K8sObjectTypeKey(
                    ProjectApiConstants.PROJECT_GROUP,
                    ProjectApiConstants.VERSION,
                    ProjectApiConstants.NODE_GROUP_RESOURCE_KIND),
            V1alpha1NodeGroup.class);
    public static final K8sObjectType<V1alpha1AipubUser> AIPUB_USER_V1ALPHA1 = new K8sObjectType<V1alpha1AipubUser>(
            new K8sObjectTypeKey(
                    ProjectApiConstants.PROJECT_GROUP,
                    ProjectApiConstants.VERSION,
                    ProjectApiConstants.AIPUB_USER_RESOURCE_KIND),
            V1alpha1AipubUser.class);
    public static final K8sObjectType<V1alpha1ImageHub> IMAGE_HUB_V1ALPHA1 = new K8sObjectType<>(
            new K8sObjectTypeKey(
                    ProjectApiConstants.PROJECT_GROUP,
                    ProjectApiConstants.VERSION,
                    ProjectApiConstants.IMAGE_HUB_RESOURCE_KIND),
            V1alpha1ImageHub.class);
    public static final K8sObjectType<V1alpha1ImageReview> IMAGE_REVIEW_V1ALPHA1 = new K8sObjectType<>(
            new K8sObjectTypeKey(
                    ProjectApiConstants.PROJECT_GROUP,
                    ProjectApiConstants.VERSION,
                    ProjectApiConstants.IMAGE_REVIEW_RESOURCE_KIND),
            V1alpha1ImageReview.class);

    public static final K8sObjectType<V1Namespace> NAMESPACE_V1 = new K8sObjectType<>(
            new K8sObjectTypeKey("core/v1", "Namespace"),
            V1Namespace.class);
    public static final K8sObjectType<V1Pod> POD_V1 = new K8sObjectType<>(
            new K8sObjectTypeKey("core/v1", "Pod"),
            V1Pod.class);
    public static final K8sObjectType<V1CronJob> CRON_JOB_V1 = new K8sObjectType<>(
            new K8sObjectTypeKey("batch/v1", "CronJob"),
            V1CronJob.class);
    public static final K8sObjectType<V1DaemonSet> DAEMON_SET_V1 = new K8sObjectType<>(
            new K8sObjectTypeKey("apps/v1", "DaemonSet"),
            V1DaemonSet.class);
    public static final K8sObjectType<V1Deployment> DEPLOYMENT_V1 = new K8sObjectType<>(
            new K8sObjectTypeKey("apps/v1", "Deployment"),
            V1Deployment.class);
    public static final K8sObjectType<V1Job> JOB_V1 = new K8sObjectType<>(
            new K8sObjectTypeKey("batch/v1", "Job"),
            V1Job.class);
    public static final K8sObjectType<V1ReplicaSet> REPLICA_SET_V1 = new K8sObjectType<>(
            new K8sObjectTypeKey("apps/v1", "ReplicaSet"),
            V1ReplicaSet.class);
    public static final K8sObjectType<V1StatefulSet> STATEFUL_SET_V1 = new K8sObjectType<>(
            new K8sObjectTypeKey("apps/v1", "StatefulSet"),
            V1StatefulSet.class);

    private K8sObjectTypeConstants() {
    }

}
