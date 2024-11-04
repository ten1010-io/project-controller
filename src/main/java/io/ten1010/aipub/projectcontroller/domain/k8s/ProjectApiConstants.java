package io.ten1010.aipub.projectcontroller.domain.k8s;

public final class ProjectApiConstants {

    public static final String GROUP = "project.aipub.ten1010.io";
    public static final String VERSION = "v1alpha1";
    public static final String API_VERSION = GROUP + "/" + VERSION;

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

    private ProjectApiConstants() {
    }

}
