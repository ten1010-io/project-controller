package io.ten1010.aipub.projectcontroller.mutating.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1Kind;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserAuthorityReviewValidateHandlerTest {

  private UserAuthorityReviewValidateHandler handler;
  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    this.handler = new UserAuthorityReviewValidateHandler();
    this.mapper = new ObjectMapperFactory().createObjectMapper();
  }

  private V1AdmissionReview createReview(String operation, String kind, ObjectNode object) {
    V1Kind v1Kind = new V1Kind();
    v1Kind.setGroup("aipub.ten1010.io");
    v1Kind.setVersion("v1alpha1");
    v1Kind.setKind(kind);

    V1AdmissionReviewRequest request = new V1AdmissionReviewRequest();
    request.setUid("test-uid");
    request.setOperation(operation);
    request.setKind(v1Kind);
    request.setObject(object);

    V1AdmissionReview review = new V1AdmissionReview();
    review.setApiVersion("admission.k8s.io/v1");
    review.setKind("AdmissionReview");
    review.setRequest(request);

    return review;
  }

  private ObjectNode objectWithOwnerRefs(String... names) {
    ObjectNode obj = this.mapper.createObjectNode();
    ObjectNode metadata = obj.putObject("metadata");
    ArrayNode ownerRefs = metadata.putArray("ownerReferences");
    for (String name : names) {
      ObjectNode ref = ownerRefs.addObject();
      ref.put("apiVersion", "v1");
      ref.put("kind", "Node");
      ref.put("name", name);
      ref.put("uid", "uid-" + name);
      ref.put("controller", true);
    }
    return obj;
  }

  private ObjectNode objectWithoutOwnerRefs() {
    ObjectNode obj = this.mapper.createObjectNode();
    obj.putObject("metadata");
    return obj;
  }

  // === canHandle ===

  @Test
  void canHandle_create_userAuthorityReview_returnsTrue() {
    V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
        objectWithOwnerRefs("dummy"));
    assertThat(this.handler.canHandle(review)).isTrue();
  }

  @Test
  void canHandle_update_returnsFalse() {
    V1AdmissionReview review = createReview("UPDATE", "UserAuthorityReview",
        objectWithOwnerRefs("dummy"));
    assertThat(this.handler.canHandle(review)).isFalse();
  }

  @Test
  void canHandle_differentKind_returnsFalse() {
    V1AdmissionReview review = createReview("CREATE", "Deployment",
        objectWithOwnerRefs("dummy"));
    assertThat(this.handler.canHandle(review)).isFalse();
  }

  // === handle — Python validate parity ===

  // Python: owner_references is None → 400
  @Test
  void noOwnerReferences_rejects400() {
    V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
        objectWithoutOwnerRefs());

    this.handler.handle(review);

    assertThat(review.getResponse().getAllowed()).isFalse();
    assertThat(review.getResponse().getStatus().getCode()).isEqualTo(400);
    assertThat(review.getResponse().getStatus().getMessage())
        .contains("Not found owner_references");
  }

  // Python: len(owner_references) != 1 → 400 (empty array)
  @Test
  void emptyOwnerReferences_rejects400() {
    ObjectNode obj = this.mapper.createObjectNode();
    obj.putObject("metadata").putArray("ownerReferences");

    V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview", obj);

    this.handler.handle(review);

    assertThat(review.getResponse().getAllowed()).isFalse();
    assertThat(review.getResponse().getStatus().getCode()).isEqualTo(400);
    assertThat(review.getResponse().getStatus().getMessage()).contains("Invalid owner_references");
  }

  // Python: len(owner_references) != 1 → 400 (2 entries)
  @Test
  void twoOwnerReferences_rejects400() {
    V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
        objectWithOwnerRefs("dummy", "extra"));

    this.handler.handle(review);

    assertThat(review.getResponse().getAllowed()).isFalse();
    assertThat(review.getResponse().getStatus().getCode()).isEqualTo(400);
  }

  // Python: owner_references[0]["name"] != "dummy" → 400
  @Test
  void nonDummyOwnerReference_rejects400() {
    V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
        objectWithOwnerRefs("not-dummy"));

    this.handler.handle(review);

    assertThat(review.getResponse().getAllowed()).isFalse();
    assertThat(review.getResponse().getStatus().getCode()).isEqualTo(400);
    assertThat(review.getResponse().getStatus().getMessage()).contains("Invalid owner_references");
  }

  // Python: valid dummy ownerReference → allowed
  @Test
  void validDummyOwnerReference_allows() {
    V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
        objectWithOwnerRefs("dummy"));

    this.handler.handle(review);

    assertThat(review.getResponse().getAllowed()).isTrue();
  }
}
