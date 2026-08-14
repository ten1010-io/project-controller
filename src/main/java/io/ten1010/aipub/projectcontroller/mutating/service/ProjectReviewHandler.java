package io.ten1010.aipub.projectcontroller.mutating.service;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.RbacV1Subject;
import io.ten1010.aipub.projectcontroller.configuration.AipubProperties;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sGroupConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

@Slf4j
public class ProjectReviewHandler extends AbstractReviewHandler<V1alpha1Project> {

  private static final String OPERATION_CREATE = "CREATE";

  private final AipubProperties aipubProperties;
  private final SubjectResolver subjectResolver;
  private final KeyResolver keyResolver;
  private final Indexer<V1alpha1Project> projectIndexer;
  private final NamespaceAllowlistResolver namespaceAllowlistResolver;

  public ProjectReviewHandler(AipubProperties aipubProperties, SubjectResolver subjectResolver,
      SharedInformerFactory sharedInformerFactory,
      NamespaceAllowlistResolver namespaceAllowlistResolver) {
    super(K8sObjectTypeConstants.PROJECT_V1ALPHA1);
    this.aipubProperties = aipubProperties;
    this.keyResolver = new KeyResolver();
    this.subjectResolver = subjectResolver;
    this.projectIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1Project.class)
        .getIndexer();
    this.namespaceAllowlistResolver = namespaceAllowlistResolver;
  }

  @Override
  public void handle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());
    Objects.requireNonNull(review.getRequest().getUserInfo());

    V1UserInfo userInfo = review.getRequest().getUserInfo();
    V1alpha1Project proj = getRequestObject(review);
    String projName = K8sObjectUtils.getName(proj);
    // 이름 기반 hard block은 CREATE에만 적용한다. UPDATE까지 거부하면 이미 존재하는 project의
    // finalizer 제거(UPDATE)가 막혀 삭제가 영구 terminating으로 교착되기 때문이다. 이 웹훅은
    // failurePolicy가 Fail이라 우회도 불가능하다.
    if (OPERATION_CREATE.equals(review.getRequest().getOperation())) {
      // reserved 네임스페이스 이름으로 project를 만들면 project 관리(quota, RBAC, secret)가 인프라
      // 네임스페이스로 새어 나간다. 그래서 아래에서 임의의 project 생성이 허용되는 system admin에게도
      // 예외 없이 막는 hard block이다.
      if (isReservedName(projName)) {
        log.debug("Project name {} is reserved", projName);
        V1AdmissionReviewUtils.reject(review, HttpStatus.CONFLICT.value(),
            String.format("%s is reserved name", projName));
        return;
      }

      // allowlist 네임스페이스 이름의 project는 관리하지 않으므로(allowlist 우선), 애매한 상태를 막기
      // 위해 생성을 거부한다. allowlist가 먼저 부여된 의미라 우선하며, system-admin 예외 없는 hard
      // block이다.
      if (this.namespaceAllowlistResolver.isAllowlisted(projName)) {
        log.debug("Project name {} matches allowlisted namespace", projName);
        V1AdmissionReviewUtils.reject(review, HttpStatus.CONFLICT.value(),
            String.format("%s is allowlisted namespace", projName));
        return;
      }
    }

    if (userInfo.getGroups() != null &&
        userInfo.getGroups().contains(K8sGroupConstants.SYSTEM_MASTERS_GROUP_NAME) ||
        userInfo.getGroups().contains(K8sGroupConstants.CLUSTER_ADMINS_GROUP_NAME) ||
        userInfo.getGroups().contains(K8sGroupConstants.AIPUB_ADMIN_GROUP_NAME)) {
      V1AdmissionReviewUtils.allow(review);
      return;
    }

    // todo should I keep project non-exist case whether added CREATE operation on mutating webhook?
    String projectKey = this.keyResolver.resolveKey(projName);
    Optional<V1alpha1Project> existingProjectOpt = Optional.ofNullable(
        this.projectIndexer.getByKey(projectKey));
    if (existingProjectOpt.isPresent()) {
      V1alpha1Project existingProject = existingProjectOpt.get();
      if (!isProjectManager(userInfo, existingProject)) {
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

  private boolean isProjectManager(V1UserInfo userInfo, V1alpha1Project project) {
    String username = userInfo.getUsername();
    List<V1alpha1ProjectMember> adminMembers = ProjectUtils.getSpecMembers(project,
        ProjectRoleEnum.PROJECT_MANAGER);
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
