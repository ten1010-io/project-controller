package io.ten1010.aipub.projectcontroller.mutating.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.ApiClient;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
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

/**
 * Port of Python WorkloadLabelMutateService (workload_label.py) + OwnerService (owner_service.py).
 * Propagates workload identification labels from parent resources to their children during CREATE
 * operations.
 */
@Slf4j
public class WorkloadLabelReviewHandler implements ReviewHandler {

  private static final String OPERATION_CREATE = "CREATE";

  private final ApiResourceDiscovery apiResourceDiscovery;
  private final ApiClient k8sApiClient;
  private final ObjectMapper mapper;

  // Port of Python: __init__(self, owner_service)
  public WorkloadLabelReviewHandler(ApiResourceDiscovery apiResourceDiscovery,
      ApiClient k8sApiClient) {
    this.apiResourceDiscovery = apiResourceDiscovery;
    this.k8sApiClient = k8sApiClient;
    this.mapper = new ObjectMapperFactory().createObjectMapper();
  }

  // Port of Python: mutate(self, request) — CREATE + namespaced 체크 부분
  @Override
  public boolean canHandle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());

    V1AdmissionReviewRequest request = review.getRequest();
    if (!OPERATION_CREATE.equals(request.getOperation())) {
      return false;
    }
    return request.getNamespace() != null && !request.getNamespace().isEmpty();
  }

  // Port of Python: mutate(self, request) — owner 조회 + 라벨 주입 로직 부분
  @Override
  public void handle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());

    V1AdmissionReviewRequest request = review.getRequest();
    Objects.requireNonNull(request.getObject());
    Objects.requireNonNull(request.getNamespace());

    log.debug("WorkloadLabel handle: namespace={}, operation={}",
        request.getNamespace(), request.getOperation());

    // Port of Python: owner_object = self._owner_service.get_owner_object(obj, namespace, output)
    JsonNode ownerObject;
    try {
      ownerObject = getOwnerObject(request.getObject(), request.getNamespace());
    } catch (Exception e) {
      // Port of Python: output.to_not_allowed(status_code=500, message=...)
      log.warn("Failed to get owner object", e);
      V1AdmissionReviewUtils.reject(review, 500, e.getMessage());
      return;
    }

    // Port of Python: if not output.allowed or owner_object is None: return output
    if (ownerObject == null) {
      log.debug("WorkloadLabel: no owner object found, allowing without mutation");
      V1AdmissionReviewUtils.allow(review);
      return;
    }

    // Port of Python: workload_labels = self._get_workload_labels_from_owner(owner_object)
    String workloadName = getWorkloadLabelFromOwner(ownerObject, LabelConstants.WORKLOAD_NAME_KEY);
    String workloadKind = getWorkloadLabelFromOwner(ownerObject, LabelConstants.WORKLOAD_KIND_KEY);

    // Port of Python: if workload_labels is None: workload_name = owner_object["metadata"]["name"] ...
    if (workloadName == null || workloadKind == null) {
      workloadName = ownerObject.path("metadata").path("name").asText();
      workloadKind = ownerObject.path("kind").asText();
      log.debug("WorkloadLabel: created labels from owner: name={}, kind={}",
          workloadName, workloadKind);
    } else {
      log.debug("WorkloadLabel: propagated labels from owner: name={}, kind={}",
          workloadName, workloadKind);
    }

    // Port of Python: labels = mutated_obj["metadata"].get("labels") ... labels.update(workload_labels)
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

    String workloadNamePath = "/metadata/labels/"
        + LabelConstants.WORKLOAD_NAME_KEY.replace("/", "~1");
    JsonPatchOperation workloadNameOp = new JsonPatchOperationBuilder()
        .add()
        .setPath(workloadNamePath)
        .setValue(this.mapper.getNodeFactory().textNode(workloadName))
        .build();
    jsonPatchBuilder.addToOperations(workloadNameOp);

    String workloadKindPath = "/metadata/labels/"
        + LabelConstants.WORKLOAD_KIND_KEY.replace("/", "~1");
    JsonPatchOperation workloadKindOp = new JsonPatchOperationBuilder()
        .add()
        .setPath(workloadKindPath)
        .setValue(this.mapper.getNodeFactory().textNode(workloadKind))
        .build();
    jsonPatchBuilder.addToOperations(workloadKindOp);

    V1AdmissionReviewUtils.allow(review, jsonPatchBuilder.build());
  }

  // Port of Python: _get_workload_labels_from_owner(self, owner_object)
  @Nullable
  private String getWorkloadLabelFromOwner(JsonNode ownerObject, String labelKey) {
    JsonNode labels = ownerObject.path("metadata").path("labels");
    if (!labels.isObject()) {
      return null;
    }
    JsonNode labelNode = labels.path(labelKey);
    if (labelNode.isMissingNode()) {
      return null;
    }
    return labelNode.asText();
  }

  // Port of Python: owner_service.get_owner_object(obj, namespace, output)
  @Nullable
  private JsonNode getOwnerObject(JsonNode objectNode, String namespace) {
    JsonNode ownerRefs = objectNode.path("metadata").path("ownerReferences");
    if (!ownerRefs.isArray()) {
      return null;
    }

    // Port of Python: find controller ownerReference
    JsonNode controllerRef = null;
    for (JsonNode ref : ownerRefs) {
      if (ref.path("controller").asBoolean(false)) {
        controllerRef = ref;
        break;
      }
    }
    if (controllerRef == null) {
      log.debug("getOwnerObject: no controller ref found");
      return null;
    }

    String apiVersion = controllerRef.path("apiVersion").asText();
    String kind = controllerRef.path("kind").asText();
    String name = controllerRef.path("name").asText();
    log.debug("getOwnerObject: controller ref apiVersion={}, kind={}, name={}",
        apiVersion, kind, name);

    // Port of Python: plural = self._api_resource_manager.get_plural(api_version, kind)
    String plural = this.apiResourceDiscovery.getPlural(apiVersion, kind);
    if (plural == null) {
      log.debug("getOwnerObject: unknown plural for {}/{}", apiVersion, kind);
      return null;
    }

    // Port of Python: group/plural → is_namespaced check
    String group = apiVersion.contains("/") ? apiVersion.split("/")[0] : "";
    String groupResource = group + "/" + plural;
    if (!this.apiResourceDiscovery.isNamespaced(groupResource)) {
      log.debug("getOwnerObject: owner not namespaced: {}", groupResource);
      return null;
    }

    // Port of Python: self._api_client.call_api(resource_path=path, method="GET", ...)
    return fetchObject(apiVersion, namespace, plural, name);
  }

  // Port of Python: api_client.call_api(resource_path=path, ...)
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
          // Port of Python: if e.status == 404: return
          if (response.code() == 404) {
            log.debug("Owner object not found: {}", path);
            return null;
          }
          // Port of Python: output.to_not_allowed(status_code=500, message=...)
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
      // Port of Python: bare except → output.to_not_allowed(500, "undefined error")
      throw new RuntimeException("Failed to get owner object with undefined error", e);
    }
  }

}
