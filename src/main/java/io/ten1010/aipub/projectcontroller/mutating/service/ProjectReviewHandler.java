package io.ten1010.aipub.projectcontroller.mutating.service;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.RbacV1Subject;
import io.ten1010.aipub.projectcontroller.configuration.AipubProperties;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sGroupConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ProjectRoleEnum;
import io.ten1010.aipub.projectcontroller.domain.k8s.SubjectResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectMember;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.ProjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.RbacSubjectUtils;
import io.ten1010.aipub.projectcontroller.mutating.V1AdmissionReviewUtils;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1UserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class ProjectReviewHandler extends AbstractReviewHandler<V1alpha1Project> {

    private final AipubProperties aipubProperties;
    private final SubjectResolver subjectResolver;
    private final KeyResolver keyResolver;
    private final Indexer<V1alpha1Project> projectIndexer;

    public ProjectReviewHandler(AipubProperties aipubProperties, SubjectResolver subjectResolver, SharedInformerFactory sharedInformerFactory) {
        super(K8sObjectTypeConstants.PROJECT_V1ALPHA1);
        this.aipubProperties = aipubProperties;
        this.keyResolver = new KeyResolver();
        this.subjectResolver = subjectResolver;
        this.projectIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1Project.class)
                .getIndexer();
    }

    @Override
    public void handle(V1AdmissionReview review) {
        Objects.requireNonNull(review.getRequest());
        Objects.requireNonNull(review.getRequest().getUserInfo());

        V1UserInfo userInfo = review.getRequest().getUserInfo();
        V1alpha1Project proj = getRequestObject(review);
        String projName = K8sObjectUtils.getName(proj);
        if (isReservedName(projName)) {
            if (userInfo.getGroups() != null &&
                    userInfo.getGroups().contains(K8sGroupConstants.SYSTEM_MASTERS_GROUP_NAME) &&
                    !userInfo.getGroups().contains(K8sGroupConstants.AIPUB_ADMIN_GROUP_NAME)) {
                log.debug("Project name {} is reserved, but allowed for system admin", projName);
                V1AdmissionReviewUtils.allow(review);
                return;
            }
            log.debug("Project name {} is reserved, not allowed to create or update for aipub admin", projName);
            V1AdmissionReviewUtils.reject(review, HttpStatus.FORBIDDEN.value(), String.format("%s is reserved name", projName));
            return;
        }

        if (userInfo.getGroups() != null &&
                (userInfo.getGroups().contains(K8sGroupConstants.SYSTEM_MASTERS_GROUP_NAME) || userInfo.getGroups().contains(K8sGroupConstants.AIPUB_ADMIN_GROUP_NAME))) {
            V1AdmissionReviewUtils.allow(review);
            return;
        }

        // todo should I keep project non-exist case whether added CREATE operation on mutating webhook?
        String projectKey = this.keyResolver.resolveKey(projName);
        Optional<V1alpha1Project> existingProjectOpt = Optional.ofNullable(this.projectIndexer.getByKey(projectKey));
        if (existingProjectOpt.isPresent()) {
            V1alpha1Project existingProject = existingProjectOpt.get();
            if (!isProjectAdmin(userInfo, existingProject)) {
                V1AdmissionReviewUtils.reject(review, HttpStatus.FORBIDDEN.value(), "Forbidden");
                return;
            }

            if (!ProjectUtils.getSpecQuota(existingProject).equals(ProjectUtils.getSpecQuota(proj)) ||
                    !ProjectUtils.getSpecBinding(existingProject).equals(ProjectUtils.getSpecBinding(proj))) {
                V1AdmissionReviewUtils.reject(review, HttpStatus.FORBIDDEN.value(), "Forbidden");
                return;
            }
        }

        V1AdmissionReviewUtils.allow(review);
    }

    private boolean isProjectAdmin(V1UserInfo userInfo, V1alpha1Project project) {
        String username = userInfo.getUsername();
        List<V1alpha1ProjectMember> adminMembers = ProjectUtils.getSpecMembers(project, ProjectRoleEnum.PROJECT_ADMIN);
        for (V1alpha1ProjectMember member : adminMembers) {
            Optional<RbacV1Subject> subjectOpt = this.subjectResolver.resolve(member);
            if (subjectOpt.isEmpty() || !RbacSubjectUtils.isUserSubject(subjectOpt.get())) {
                continue;
            }

            RbacV1Subject subject = subjectOpt.get();
            if (subject.getName().equals(username)) {
                return true;
            }
        }

        return false;
    }

    private boolean isReservedName(String name) {
        List<String> reservedNames = this.aipubProperties.getReservedNamespace();
        return reservedNames.contains(name);
    }

}
