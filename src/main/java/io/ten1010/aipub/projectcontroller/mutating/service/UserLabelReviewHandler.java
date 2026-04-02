package io.ten1010.aipub.projectcontroller.mutating.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.ApiClient;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
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
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

@Slf4j
public class UserLabelReviewHandler implements ReviewHandler {

  private static final String OPERATION_CREATE = "CREATE";

  // v2 테스트용: Python 원본과 병행 운영하여 비교. 정식 전환 시 LabelConstants 원본 키로 복원.
  private static final String USERNAME_LABEL_KEY_V2 =
      LabelConstants.OBJECT_OWN_USERNAME_KEY + "-v2";
  private static final String USERID_LABEL_KEY_V2 =
      LabelConstants.OBJECT_OWN_USERID_KEY + "-v2";

  private final UserInfoAnalyzer userInfoAnalyzer;
  private final ApiResourceDiscovery apiResourceDiscovery;
  private final ApiClient k8sApiClient;
  private final ObjectMapper mapper;

  public UserLabelReviewHandler(UserInfoAnalyzer userInfoAnalyzer,
      ApiResourceDiscovery apiResourceDiscovery, ApiClient k8sApiClient) {
    this.userInfoAnalyzer = userInfoAnalyzer;
    this.apiResourceDiscovery = apiResourceDiscovery;
    this.k8sApiClient = k8sApiClient;
    this.mapper = new ObjectMapperFactory().createObjectMapper();
  }

  @Override
  public boolean canHandle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());

    V1AdmissionReviewRequest request = review.getRequest();
    if (!OPERATION_CREATE.equals(request.getOperation())) {
      return false;
    }
    return request.getNamespace() != null && !request.getNamespace().isEmpty();
  }

  @Override
  public void handle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());

    V1AdmissionReviewRequest request = review.getRequest();
    Objects.requireNonNull(request.getUserInfo());
    Objects.requireNonNull(request.getObject());
    Objects.requireNonNull(request.getNamespace());

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

    if (analysis.isAipubMember() && analysis.getAipubUser().isPresent()) {
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
      log.debug("UserLabel: not aipub member, looking up owner labels");
      String[] ownerLabels;
      try {
        ownerLabels = getLabelsFromOwner(request.getObject(), request.getNamespace());
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
        + USERNAME_LABEL_KEY_V2.replace("/", "~1");
    JsonPatchOperation usernamePatchOp = new JsonPatchOperationBuilder()
        .add()
        .setPath(usernameLabelPath)
        .setValue(this.mapper.getNodeFactory().textNode(username))
        .build();
    jsonPatchBuilder.addToOperations(usernamePatchOp);

    String useridLabelPath = "/metadata/labels/"
        + USERID_LABEL_KEY_V2.replace("/", "~1");
    JsonPatchOperation useridPatchOp = new JsonPatchOperationBuilder()
        .add()
        .setPath(useridLabelPath)
        .setValue(this.mapper.getNodeFactory().textNode(userid))
        .build();
    jsonPatchBuilder.addToOperations(useridPatchOp);

    V1AdmissionReviewUtils.allowMerging(review, jsonPatchBuilder.build());
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

    JsonNode ownerLabels = ownerObject.path("metadata").path("labels");
    if (!ownerLabels.isObject()) {
      log.debug("getLabelsFromOwner: owner has no labels");
      return null;
    }

    JsonNode usernameNode = ownerLabels.get(USERNAME_LABEL_KEY_V2);
    JsonNode useridNode = ownerLabels.get(USERID_LABEL_KEY_V2);
    if (usernameNode == null || useridNode == null) {
      log.debug("getLabelsFromOwner: owner missing username/userid labels. labels={}", ownerLabels);
      return null;
    }

    return new String[]{usernameNode.textValue(), useridNode.textValue()};
  }

  @Nullable
  private JsonNode fetchObject(String apiVersion, String namespace, String plural, String name) {
    String path;
    if (apiVersion.contains("/")) {
      path = "/apis/" + apiVersion + "/namespaces/" + namespace + "/" + plural + "/" + name;
    } else {
      path = "/api/" + apiVersion + "/namespaces/" + namespace + "/" + plural + "/" + name;
    }

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
