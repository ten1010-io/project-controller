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
  private static final String USERID_LABEL_KEY =
      "aipub.ten1010.io/userid";

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

    log.info("UserLabel handle: user={}, namespace={}, operation={}",
        request.getUserInfo().getUsername(), request.getNamespace(), request.getOperation());

    UserInfoAnalysis analysis;
    try {
      analysis = this.userInfoAnalyzer.analyze(request.getUserInfo());
    } catch (Exception e) {
      log.info("Failed to analyze user info, allowing without mutation", e);
      V1AdmissionReviewUtils.allow(review);
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
      log.info("UserLabel: direct aipub member, username={}, userid={}", username, userid);
    } else {
      log.info("UserLabel: not aipub member, looking up owner labels");
      String[] ownerLabels = getLabelsFromOwner(request.getObject(), request.getNamespace());
      if (ownerLabels == null) {
        log.info("UserLabel: no owner labels found, allowing without mutation");
        V1AdmissionReviewUtils.allow(review);
        return;
      }
      username = ownerLabels[0];
      userid = ownerLabels[1];
      log.info("UserLabel: propagated from owner, username={}, userid={}", username, userid);
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
        + LabelConstants.OBJECT_OWN_USERNAME_KEY.replace("/", "~1");
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

    V1AdmissionReviewUtils.allow(review, jsonPatchBuilder.build());
  }

  @Nullable
  private String[] getLabelsFromOwner(JsonNode objectNode, String namespace) {
    JsonNode ownerRefs = objectNode.path("metadata").path("ownerReferences");
    if (!ownerRefs.isArray()) {
      return null;
    }

    JsonNode controllerRef = null;
    for (JsonNode ref : ownerRefs) {
      if (ref.path("controller").asBoolean(false)) {
        controllerRef = ref;
        break;
      }
    }
    if (controllerRef == null) {
      log.info("getLabelsFromOwner: no controller ref found");
      return null;
    }

    String apiVersion = controllerRef.path("apiVersion").asText();
    String kind = controllerRef.path("kind").asText();
    String name = controllerRef.path("name").asText();
    log.info("getLabelsFromOwner: controller ref apiVersion={}, kind={}, name={}", apiVersion, kind, name);

    String plural = this.apiResourceDiscovery.getPlural(apiVersion, kind);
    if (plural == null) {
      log.info("getLabelsFromOwner: unknown plural for {}/{}", apiVersion, kind);
      return null;
    }
    log.info("getLabelsFromOwner: plural={}", plural);

    String group = apiVersion.contains("/") ? apiVersion.split("/")[0] : "";
    String groupResource = group + "/" + plural;
    if (!this.apiResourceDiscovery.isNamespaced(groupResource)) {
      log.info("getLabelsFromOwner: owner not namespaced: {}", groupResource);
      return null;
    }

    JsonNode ownerObject = fetchObject(apiVersion, namespace, plural, name);
    if (ownerObject == null) {
      log.info("getLabelsFromOwner: failed to fetch owner object");
      return null;
    }

    JsonNode ownerLabels = ownerObject.path("metadata").path("labels");
    if (!ownerLabels.isObject()) {
      log.info("getLabelsFromOwner: owner has no labels");
      return null;
    }

    JsonNode usernameNode = ownerLabels.path(LabelConstants.OBJECT_OWN_USERNAME_KEY);
    JsonNode useridNode = ownerLabels.path(USERID_LABEL_KEY);
    if (usernameNode.isMissingNode() || useridNode.isMissingNode()) {
      log.info("getLabelsFromOwner: owner missing username/userid labels. labels={}", ownerLabels);
      return null;
    }

    return new String[]{usernameNode.asText(), useridNode.asText()};
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
          } else {
            log.warn("Failed to fetch owner object: {} status={}", path, response.code());
          }
          return null;
        }
        if (response.body() == null) {
          return null;
        }
        return this.mapper.readTree(response.body().string());
      }
    } catch (Exception e) {
      log.warn("Failed to fetch owner object: {}", path, e);
      return null;
    }
  }

}
