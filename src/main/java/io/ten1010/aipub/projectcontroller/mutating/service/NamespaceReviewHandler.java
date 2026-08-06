package io.ten1010.aipub.projectcontroller.mutating.service;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.ten1010.aipub.projectcontroller.configuration.AipubProperties;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sGroupConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.SubjectResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.mutating.V1AdmissionReviewUtils;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1UserInfo;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

@Slf4j
public class NamespaceReviewHandler extends AbstractReviewHandler<V1Namespace> {

  private static final String OPERATION_CREATE = "CREATE";
  private static final String OPERATION_UPDATE = "UPDATE";

  private final AipubProperties aipubProperties;
  private final KeyResolver keyResolver;
  private final SubjectResolver subjectResolver;
  private final Indexer<V1alpha1Project> projectIndexer;

  public NamespaceReviewHandler(AipubProperties aipubProperties, SubjectResolver subjectResolver,
      SharedInformerFactory sharedInformerFactory) {
    super(K8sObjectTypeConstants.NAMESPACE_V1);
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

    String operation = review.getRequest().getOperation();
    if (OPERATION_CREATE.equals(operation) || OPERATION_UPDATE.equals(operation)) {
      handleAllowlistLabeling(review);
      return;
    }

    handleReservedDeletion(review);
  }

  /**
   * 동명의 project가 아직 있는(종료 중인 경우 포함) 네임스페이스에 allowlist 라벨을 붙이는 것을
   * 거부한다. project 격리가 먼저 부여된 의미라 우선하며, allowlist 네임스페이스 이름으로 project를
   * 만드는 것을 거부하는 {@code ProjectReviewHandler}와 대칭이다. system-admin 예외 없는 hard
   * block이다. 종료 중인 project도 거부하는데, 그 네임스페이스는 project 소유(ownerReference)라
   * project와 함께 garbage collection으로 지워지므로, allowlist를 붙여봐야 곧 사라질 네임스페이스에
   * 라벨만 남기기 때문이다.
   *
   * <p>원하는 상태는 informer 캐시가 아니라 요청 객체에서 읽는다. UPDATE 시 캐시에는 patch 이전
   * 네임스페이스가 남아 있어 새로 붙는 라벨을 놓치기 때문이다.
   */
  private void handleAllowlistLabeling(V1AdmissionReview review) {
    V1Namespace desired = getRequestObject(review);
    if (!NamespaceAllowlistResolver.isAllowlisted(desired)) {
      V1AdmissionReviewUtils.allow(review);
      return;
    }

    String namespaceName = K8sObjectUtils.getName(desired);
    Optional<V1alpha1Project> projectOpt = Optional.ofNullable(
        this.projectIndexer.getByKey(this.keyResolver.resolveKey(namespaceName)));
    if (projectOpt.isPresent()) {
      log.debug("Namespace {} cannot be allowlisted while a project with the same name exists",
          namespaceName);
      V1AdmissionReviewUtils.reject(review, HttpStatus.CONFLICT.value(),
          String.format("%s still has a project of the same name; allowlisting is allowed only"
              + " after that project is deleted and the namespace is released", namespaceName));
      return;
    }

    V1AdmissionReviewUtils.allow(review);
  }

  /**
   * reserved 네임스페이스 판정에는 이름만 필요하므로 요청에서 직접 읽는다. informer 캐시를 거치면
   * 캐시에 아직 없는 네임스페이스의 DELETE 요청에서 NPE가 나고, failurePolicy가 Ignore라 가드가
   * 조용히 무력화된다.
   */
  private void handleReservedDeletion(V1AdmissionReview review) {
    String namespaceName = getNamespaceName(review);
    if (isReservedName(namespaceName)) {
      V1UserInfo userInfo = review.getRequest().getUserInfo();
      if (userInfo.getGroups() != null &&
          (userInfo.getGroups().contains(K8sGroupConstants.SYSTEM_MASTERS_GROUP_NAME) ||
              userInfo.getGroups().contains(K8sGroupConstants.CLUSTER_ADMINS_GROUP_NAME)) &&
          !(userInfo.getGroups().contains(K8sGroupConstants.AIPUB_ADMIN_GROUP_NAME))) {
        log.debug("Allowed namespace {} deletion because requester is system admin", namespaceName);
        V1AdmissionReviewUtils.allow(review);
        return;
      }
      log.debug("Namespace {} is reserved", namespaceName);
      V1AdmissionReviewUtils.reject(review, HttpStatus.CONFLICT.value(),
          String.format("%s is reserved name", namespaceName));
      return;
    }

    V1AdmissionReviewUtils.allow(review);
  }

  private String getNamespaceName(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());
    Objects.requireNonNull(review.getRequest().getName());

    return review.getRequest().getName();
  }

  private boolean isReservedName(String name) {
    return this.aipubProperties.getReservedNamespace().contains(name);
  }

}
