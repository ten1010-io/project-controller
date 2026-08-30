package io.ten1010.aipub.projectcontroller.mutating.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.ApiClient;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.ProjectApiConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.mutating.V1AdmissionReviewUtils;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import io.ten1010.common.jsonpatch.JsonPatchBuilder;
import io.ten1010.common.jsonpatch.JsonPatchOperationBuilder;
import io.ten1010.common.jsonpatch.dto.JsonPatchOperation;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

@Slf4j
public class UserLabelReviewHandler implements ReviewHandler {

  private static final String OPERATION_CREATE = "CREATE";

  private static final String USERNAME_LABEL_KEY =
      LabelConstants.OBJECT_OWN_USERNAME_KEY;
  private static final String USERID_LABEL_KEY =
      LabelConstants.OBJECT_OWN_USERID_KEY;
  private static final String CLUSTER_VOLUME_OWNER_LABEL_KEY =
      LabelConstants.CLUSTER_VOLUME_OWNER_KEY;
  /** RFC 1123 DNS label — ClusterVolume CRD 가 metadata.name 에 강제하는 형식과 동일. */
  private static final Pattern DNS_LABEL_PATTERN =
      Pattern.compile("^[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?$");

  private final UserInfoAnalyzer userInfoAnalyzer;
  private final ApiResourceDiscovery apiResourceDiscovery;
  private final ApiClient k8sApiClient;
  private final NamespaceAllowlistResolver namespaceAllowlistResolver;
  private final ObjectMapper mapper;

  public UserLabelReviewHandler(UserInfoAnalyzer userInfoAnalyzer,
      ApiResourceDiscovery apiResourceDiscovery, ApiClient k8sApiClient,
      NamespaceAllowlistResolver namespaceAllowlistResolver) {
    this.userInfoAnalyzer = userInfoAnalyzer;
    this.apiResourceDiscovery = apiResourceDiscovery;
    this.k8sApiClient = k8sApiClient;
    this.namespaceAllowlistResolver = namespaceAllowlistResolver;
    this.mapper = new ObjectMapperFactory().createObjectMapper();
  }

  @Override
  public boolean canHandle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());

    V1AdmissionReviewRequest request = review.getRequest();
    if (!OPERATION_CREATE.equals(request.getOperation())) {
      return false;
    }
    // cluster-scoped 리소스 중 라벨 주입 대상은 Namespace, ClusterVolume, PersistentVolume 이다.
    // ClusterVolume 은 라벨이 유일한 소유자 기록이다 — 생성자 신원은 어드미션 시점에만
    // 존재하고(request.userInfo) 오브젝트에는 남지 않으므로, 여기서 찍어두지 않으면
    // 이후 리컨실러·GUI 가 소유자를 알 방법이 없다.
    // PersistentVolume 은 ClusterVolume 컨트롤러가 만드는 복제/앵커 PV 가 대상이다 — 부모 CV 의
    // 소유자 라벨을 owner 라벨(clustervolumes.aipub.ten1010.io/owner)을 따라 전파한다.
    if (V1AdmissionReviewUtils.isNamespaceRequest(request)
        || V1AdmissionReviewUtils.isClusterVolumeRequest(request)
        || V1AdmissionReviewUtils.isPersistentVolumeRequest(request)) {
      return true;
    }
    // 소유권 대상(OwnershipPolicy.OWNED_TARGETS)은 전부 네임스페이스 리소스다
    return request.getNamespace() != null && !request.getNamespace().isEmpty();
  }

  @Override
  public void handle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());

    V1AdmissionReviewRequest request = review.getRequest();
    Objects.requireNonNull(request.getUserInfo());
    Objects.requireNonNull(request.getObject());

    // Namespace 자신의 CREATE 는 allowlist 여부와 무관하게 라벨을 주입한다. allowlist 스킵은
    // "allowlist 네임스페이스 안의 리소스"에 대한 규칙이지, 네임스페이스 오브젝트 자체의 규칙이 아니다.
    // ClusterVolume·PersistentVolume 도 cluster-scoped 라 적용할 대상 네임스페이스가 없다.
    boolean namespaceRequest = V1AdmissionReviewUtils.isNamespaceRequest(request);
    boolean clusterVolumeRequest = V1AdmissionReviewUtils.isClusterVolumeRequest(request);
    boolean clusterScopedRequest = namespaceRequest
        || clusterVolumeRequest
        || V1AdmissionReviewUtils.isPersistentVolumeRequest(request);
    if (!clusterScopedRequest) {
      Objects.requireNonNull(request.getNamespace());
      if (this.namespaceAllowlistResolver.isAllowlisted(request.getNamespace())) {
        V1AdmissionReviewUtils.allowMerging(review);
        return;
      }
    }

    log.debug("UserLabel handle: user={}, namespace={}, operation={}",
        request.getUserInfo().getUsername(), request.getNamespace(), request.getOperation());

    UserInfoAnalysis analysis;
    try {
      analysis = this.userInfoAnalyzer.analyzeV2(request.getUserInfo());
    } catch (Exception e) {
      // Python: get_aipub_user non-404 ApiException → 500
      log.warn("Failed to analyze user info", e);
      V1AdmissionReviewUtils.reject(review, 500,
          "Failed to get aipub user with following error. " + e.getMessage());
      return;
    }

    String username;
    String userid;

    // Namespace·ClusterVolume 은 admin 도 라벨 대상이다. admin 토큰에는 aipub-member 그룹이
    // 없어(k8s RBAC 도 oidc:aipub-admin/oidc:aipub-member 별개 그룹) member 검사만으로는 admin 이
    // 만든 오브젝트가 시스템 소유물로 분류된다. CV 는 라벨이 유일한 소유자 기록이고 자식(PVC/PV)
    // 라벨 전파의 원천이므로, admin 이 직접 만든 CV 도 찍어야 소유자·자식 추적이 성립한다.
    // namespaced 리소스는 기존 quota/소유권 동작 보존을 위해 member 만 유지한다.
    boolean labelSubject = analysis.isAipubMember()
        || ((namespaceRequest || clusterVolumeRequest) && analysis.isAipubAdmin());

    if (labelSubject && analysis.getAipubUser().isPresent()) {
      V1alpha1AipubUser aipubUser = analysis.getAipubUser().get();
      if (aipubUser.getSpec() == null || aipubUser.getSpec().getId() == null) {
        V1AdmissionReviewUtils.reject(review, HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Not found user id of aipub user: " + K8sObjectUtils.getName(aipubUser));
        return;
      }
      username = K8sObjectUtils.getName(aipubUser);
      userid = aipubUser.getSpec().getId();
      log.debug("UserLabel: direct aipub member, username={}, userid={}", username, userid);
    } else if (analysis.isAipubMember()) {
      V1AdmissionReviewUtils.reject(review, 400,
          "Not found aipub user: " + analysis.getUsername());
      return;
    } else {
      // 비멤버(시스템 컴포넌트·백엔드 SA·다른 컨트롤러)가 만든 오브젝트는 부모에서 라벨을
      // 전파한다. 전파 경로가 없으면 라벨 없이 허용한다.
      log.debug("UserLabel: not aipub member, looking up owner labels");
      String[] ownerLabels;
      try {
        ownerLabels = resolveOwnerLabels(request, clusterScopedRequest);
      } catch (Exception e) {
        // Python: owner_service.get_owner_object non-404 ApiException → 500
        log.warn("Failed to get owner object", e);
        V1AdmissionReviewUtils.reject(review, 500, e.getMessage());
        return;
      }
      if (ownerLabels == null) {
        log.debug("UserLabel: no owner labels found, allowing without mutation");
        V1AdmissionReviewUtils.allowMerging(review);
        return;
      }
      username = ownerLabels[0];
      userid = ownerLabels[1];
      log.debug("UserLabel: propagated from owner, username={}, userid={}", username, userid);
    }

    JsonNode objectNode = request.getObject();
    JsonNode existingLabels = objectNode.path("metadata").path("labels");

    JsonPatchBuilder jsonPatchBuilder = new JsonPatchBuilder();

    if (!existingLabels.isObject()) {
      JsonPatchOperation initLabelsOp = new JsonPatchOperationBuilder()
          .add()
          .setPath("/metadata/labels")
          .setValue(this.mapper.createObjectNode())
          .build();
      jsonPatchBuilder.addToOperations(initLabelsOp);
    }

    String usernameLabelPath = "/metadata/labels/"
        + USERNAME_LABEL_KEY.replace("/", "~1");
    JsonPatchOperation usernamePatchOp = new JsonPatchOperationBuilder()
        .add()
        .setPath(usernameLabelPath)
        .setValue(this.mapper.getNodeFactory().textNode(username))
        .build();
    jsonPatchBuilder.addToOperations(usernamePatchOp);

    String useridLabelPath = "/metadata/labels/"
        + USERID_LABEL_KEY.replace("/", "~1");
    JsonPatchOperation useridPatchOp = new JsonPatchOperationBuilder()
        .add()
        .setPath(useridLabelPath)
        .setValue(this.mapper.getNodeFactory().textNode(userid))
        .build();
    jsonPatchBuilder.addToOperations(useridPatchOp);

    V1AdmissionReviewUtils.allowMerging(review, jsonPatchBuilder.build());
  }

  /**
   * 비멤버가 만든 오브젝트의 소유자 라벨을 부모에서 찾는다. 우선순위:
   * <ol>
   *   <li>ClusterVolume owner 라벨({@code clustervolumes.aipub.ten1010.io/owner=<CV명>}) —
   *   ClusterVolume 컨트롤러가 만든 복제/앵커 PVC·PV 는 이 라벨로 부모 CV 를 가리킨다.
   *   부모가 cluster-scoped 라 아래 controller ownerReference 경로로는 닿지 않는다.
   *   PVC·PV 에만 적용한다 — 다른 kind 가 이 라벨을 갖는 경우(예: 워크로드 템플릿 라벨을 상속한
   *   Pod)는 CV 자식이 아니므로 기존 경로를 그대로 탄다.</li>
   *   <li>controller ownerReference — 기존 워크로드 전파 경로. 부모가 네임스페이스 리소스일
   *   때만 성립하므로 cluster-scoped 요청(Namespace·ClusterVolume·PersistentVolume)에는
   *   적용하지 않는다.</li>
   * </ol>
   *
   * @return {username, userid} 또는 전파할 소유자가 없으면 null
   */
  @Nullable
  private String[] resolveOwnerLabels(V1AdmissionReviewRequest request,
      boolean clusterScopedRequest) {
    JsonNode objectNode = request.getObject();
    if (isClusterVolumeChildCandidate(request)) {
      String clusterVolumeName = getClusterVolumeOwnerName(objectNode);
      if (clusterVolumeName != null) {
        return getLabelsFromClusterVolume(clusterVolumeName);
      }
    }
    if (clusterScopedRequest) {
      log.debug("resolveOwnerLabels: cluster-scoped object without ClusterVolume owner label, "
          + "no propagation path");
      return null;
    }
    return getLabelsFromOwner(objectNode, request.getNamespace());
  }

  private static boolean isClusterVolumeChildCandidate(V1AdmissionReviewRequest request) {
    return V1AdmissionReviewUtils.isPersistentVolumeClaimRequest(request)
        || V1AdmissionReviewUtils.isPersistentVolumeRequest(request);
  }

  /**
   * owner 라벨 값(= 부모 ClusterVolume 이름)을 읽는다. CRD 가 CV 이름을 DNS label(63자)로 강제하므로
   * 같은 형식만 받는다 — mutating admission 은 스키마 검증 전이라 라벨 값이 아직 보장되지 않는데,
   * 이 값이 그대로 GET 경로에 들어가므로 형식이 다르면 CV 자식이 아닌 것으로 보고 null 을 돌려준다.
   */
  @Nullable
  private String getClusterVolumeOwnerName(JsonNode objectNode) {
    JsonNode ownerNode = objectNode.path("metadata").path("labels")
        .get(CLUSTER_VOLUME_OWNER_LABEL_KEY);
    if (ownerNode == null || !ownerNode.isTextual()) {
      return null;
    }
    String name = ownerNode.textValue();
    if (!DNS_LABEL_PATTERN.matcher(name).matches()) {
      log.debug("getClusterVolumeOwnerName: owner label is not a DNS label, ignoring: {}", name);
      return null;
    }
    return name;
  }

  /**
   * owner 라벨이 가리키는 ClusterVolume 의 소유자 라벨을 읽는다. ClusterVolume 이 없거나(404)
   * 라벨이 없으면 null — 자식은 라벨 없이 통과한다. 그 외 API 오류는 예외로 전파되어 500 으로
   * 거부된다(기존 controller owner 조회와 같은 계약).
   */
  @Nullable
  private String[] getLabelsFromClusterVolume(String name) {
    log.debug("getLabelsFromClusterVolume: name={}", name);
    JsonNode clusterVolume = fetchClusterScopedObject(ProjectApiConstants.AIPUB_API_VERSION,
        ProjectApiConstants.CLUSTER_VOLUME_RESOURCE_PLURAL, name);
    if (clusterVolume == null) {
      log.debug("getLabelsFromClusterVolume: ClusterVolume not found: {}", name);
      return null;
    }
    return extractUserLabels(clusterVolume, "ClusterVolume " + name);
  }

  @Nullable
  private String[] getLabelsFromOwner(JsonNode objectNode, String namespace) {
    JsonNode ownerRefs = objectNode.path("metadata").path("ownerReferences");
    if (!ownerRefs.isArray()) {
      return null;
    }

    JsonNode controllerRef = null;
    for (JsonNode ref : ownerRefs) {
      JsonNode controllerNode = ref.get("controller");
      if (controllerNode != null && controllerNode.booleanValue()) {
        controllerRef = ref;
        break;
      }
    }
    if (controllerRef == null) {
      log.debug("getLabelsFromOwner: no controller ref found");
      return null;
    }

    JsonNode apiVersionNode = controllerRef.get("apiVersion");
    JsonNode kindNode = controllerRef.get("kind");
    JsonNode nameNode = controllerRef.get("name");
    if (apiVersionNode == null || kindNode == null || nameNode == null) {
      log.debug("getLabelsFromOwner: controller ref missing required fields");
      return null;
    }
    String apiVersion = apiVersionNode.textValue();
    String kind = kindNode.textValue();
    String name = nameNode.textValue();
    log.debug("getLabelsFromOwner: controller ref apiVersion={}, kind={}, name={}", apiVersion, kind, name);

    String plural = this.apiResourceDiscovery.getPlural(apiVersion, kind);
    if (plural == null) {
      log.debug("getLabelsFromOwner: unknown plural for {}/{}", apiVersion, kind);
      return null;
    }
    log.debug("getLabelsFromOwner: plural={}", plural);

    String group = apiVersion.contains("/") ? apiVersion.split("/")[0] : "";
    String groupResource = group + "/" + plural;
    // Python: is_namespaced에서 Exception 발생 시 catch 없이 상위로 전파 → 500
    if (!this.apiResourceDiscovery.isNamespaced(groupResource)) {
      log.debug("getLabelsFromOwner: owner not namespaced: {}", groupResource);
      return null;
    }

    JsonNode ownerObject = fetchObject(apiVersion, namespace, plural, name);
    if (ownerObject == null) {
      log.debug("getLabelsFromOwner: failed to fetch owner object");
      return null;
    }

    return extractUserLabels(ownerObject, "owner " + kind + "/" + name);
  }

  /**
   * 부모 오브젝트의 username/userid 라벨 쌍을 읽는다. 둘 중 하나라도 없거나 문자열이 아니면 null.
   */
  @Nullable
  private String[] extractUserLabels(JsonNode ownerObject, String ownerDescription) {
    JsonNode ownerLabels = ownerObject.path("metadata").path("labels");
    if (!ownerLabels.isObject()) {
      log.debug("extractUserLabels: {} has no labels", ownerDescription);
      return null;
    }

    JsonNode usernameNode = ownerLabels.get(USERNAME_LABEL_KEY);
    JsonNode useridNode = ownerLabels.get(USERID_LABEL_KEY);
    if (usernameNode == null || useridNode == null) {
      log.debug("extractUserLabels: {} missing username/userid labels. labels={}",
          ownerDescription, ownerLabels);
      return null;
    }

    String username = usernameNode.textValue();
    String userid = useridNode.textValue();
    if (username == null || userid == null) {
      log.debug("extractUserLabels: {} labels are not string type. labels={}",
          ownerDescription, ownerLabels);
      return null;
    }

    return new String[]{username, userid};
  }

  @Nullable
  private JsonNode fetchObject(String apiVersion, String namespace, String plural, String name) {
    return fetchObjectByPath(
        apiPathPrefix(apiVersion) + "/namespaces/" + namespace + "/" + plural + "/" + name);
  }

  @Nullable
  private JsonNode fetchClusterScopedObject(String apiVersion, String plural, String name) {
    return fetchObjectByPath(apiPathPrefix(apiVersion) + "/" + plural + "/" + name);
  }

  private static String apiPathPrefix(String apiVersion) {
    return (apiVersion.contains("/") ? "/apis/" : "/api/") + apiVersion;
  }

  @Nullable
  private JsonNode fetchObjectByPath(String path) {
    try {
      Call call = this.k8sApiClient.buildCall(
          this.k8sApiClient.getBasePath(), path, "GET",
          List.of(), List.of(),
          null,
          Map.of(), Map.of(), Map.of(),
          new String[]{"BearerToken"}, null);
      try (Response response = call.execute()) {
        if (!response.isSuccessful()) {
          if (response.code() == 404) {
            log.debug("Owner object not found: {}", path);
            return null;
          }
          // Python: ApiException non-404 → output.to_not_allowed(500)
          throw new RuntimeException(
              "Failed to get owner object with APIException. status code: " + response.code());
        }
        if (response.body() == null) {
          return null;
        }
        return this.mapper.readTree(response.body().string());
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      // Python: bare except → output.to_not_allowed(500, "undefined error")
      throw new RuntimeException("Failed to get owner object with undefined error", e);
    }
  }

}
