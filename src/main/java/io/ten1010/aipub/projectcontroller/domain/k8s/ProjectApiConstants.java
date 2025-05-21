package io.ten1010.aipub.projectcontroller.domain.k8s;

public final class ProjectApiConstants {

    public static final String PROJECT_GROUP = "project.aipub.ten1010.io";
    public static final String AIPUB_GROUP = "aipub.ten1010.io";
    public static final String COASTER_GROUP = "coaster.ten1010.io";
    public static final String VERSION = "v1alpha1";
    public static final String PROJECT_API_VERSION = PROJECT_GROUP + "/" + VERSION;
    public static final String AIPUB_API_VERSION = AIPUB_GROUP + "/" + VERSION;
    public static final String COASTER_API_VERSION = COASTER_GROUP + "/" + VERSION;

    public static final String PROJECT_RESOURCE_KIND = "Project";
    public static final String PROJECT_RESOURCE_PLURAL = "projects";

    public static final String NODE_GROUP_RESOURCE_KIND = "NodeGroup";
    public static final String NODE_GROUP_RESOURCE_PLURAL = "nodegroups";

    public static final String AIPUB_USER_RESOURCE_KIND = "AipubUser";
    public static final String AIPUB_USER_RESOURCE_PLURAL = "aipubusers";

    public static final String IMAGE_NAMESPACE_RESOURCE_KIND = "ImageNamespace";
    public static final String IMAGE_NAMESPACE_RESOURCE_PLURAL = "imagenamespaces";

    public static final String IMAGE_REVIEW_RESOURCE_KIND = "ImageReview";
    public static final String IMAGE_REVIEW_RESOURCE_PLURAL = "imagereviews";

    public static final String RESOURCE_SET_RESOURCE_KIND = "ResourceSet";
    public static final String RESOURCE_SET_RESOURCE_PLURAL = "resourcesets";

    public static final String NODE_RESOURCE_STATUS_RESOURCE_KIND = "NodeResourceStatus";
    public static final String NODE_RESOURCE_STATUS_RESOURCE_PLURAL = "noderesourcestatuses";

    public static final String GPU_QUOTA_RESOURCE_KIND = "GpuQuota";
    public static final String GPU_QUOTA_RESOURCE_PLURAL = "gpuquotas";

    private ProjectApiConstants() {
    }

}
