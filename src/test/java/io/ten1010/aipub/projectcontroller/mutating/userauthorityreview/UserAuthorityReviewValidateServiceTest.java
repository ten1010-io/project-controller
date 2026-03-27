package io.ten1010.aipub.projectcontroller.mutating.userauthorityreview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1Kind;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserAuthorityReviewValidateServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    UserAuthorityReviewValidateService service;

    @BeforeEach
    void setUp() {
        this.service = new UserAuthorityReviewValidateService();
    }

    @Test
    void should_skip_non_create_operation() {
        V1AdmissionReviewRequest request = buildRequest("UPDATE", "UserAuthorityReview", null);
        ValidateResult result = this.service.validate(request);

        Assertions.assertTrue(result.isAllowed());
    }

    @Test
    void should_skip_non_matching_kind() {
        V1AdmissionReviewRequest request = buildRequest("CREATE", "Pod", null);
        ValidateResult result = this.service.validate(request);

        Assertions.assertTrue(result.isAllowed());
    }

    @Test
    void should_reject_when_no_owner_references() {
        ObjectNode obj = MAPPER.createObjectNode();
        obj.putObject("metadata");
        V1AdmissionReviewRequest request = buildRequest("CREATE", "UserAuthorityReview", obj);

        ValidateResult result = this.service.validate(request);

        Assertions.assertFalse(result.isAllowed());
        Assertions.assertEquals(400, result.getStatusCode());
        Assertions.assertEquals("Not found owner_references for user authority review", result.getMessage());
    }

    @Test
    void should_reject_when_owner_references_is_empty() {
        ObjectNode obj = MAPPER.createObjectNode();
        ObjectNode metadata = obj.putObject("metadata");
        metadata.putArray("ownerReferences");
        V1AdmissionReviewRequest request = buildRequest("CREATE", "UserAuthorityReview", obj);

        ValidateResult result = this.service.validate(request);

        Assertions.assertFalse(result.isAllowed());
        Assertions.assertEquals("Invalid owner_references", result.getMessage());
    }

    @Test
    void should_reject_when_multiple_owner_references() {
        ObjectNode obj = MAPPER.createObjectNode();
        ObjectNode metadata = obj.putObject("metadata");
        ArrayNode ownerRefs = metadata.putArray("ownerReferences");
        ownerRefs.addObject().put("name", "dummy");
        ownerRefs.addObject().put("name", "other");
        V1AdmissionReviewRequest request = buildRequest("CREATE", "UserAuthorityReview", obj);

        ValidateResult result = this.service.validate(request);

        Assertions.assertFalse(result.isAllowed());
        Assertions.assertEquals("Invalid owner_references", result.getMessage());
    }

    @Test
    void should_reject_when_owner_reference_name_is_not_dummy() {
        ObjectNode obj = MAPPER.createObjectNode();
        ObjectNode metadata = obj.putObject("metadata");
        ArrayNode ownerRefs = metadata.putArray("ownerReferences");
        ownerRefs.addObject().put("name", "not-dummy");
        V1AdmissionReviewRequest request = buildRequest("CREATE", "UserAuthorityReview", obj);

        ValidateResult result = this.service.validate(request);

        Assertions.assertFalse(result.isAllowed());
        Assertions.assertEquals("Invalid owner_references", result.getMessage());
    }

    @Test
    void should_allow_with_correct_dummy_owner_reference() {
        ObjectNode obj = MAPPER.createObjectNode();
        ObjectNode metadata = obj.putObject("metadata");
        ArrayNode ownerRefs = metadata.putArray("ownerReferences");
        ownerRefs.addObject().put("name", "dummy");
        V1AdmissionReviewRequest request = buildRequest("CREATE", "UserAuthorityReview", obj);

        ValidateResult result = this.service.validate(request);

        Assertions.assertTrue(result.isAllowed());
    }

    private V1AdmissionReviewRequest buildRequest(String operation, String kindName, ObjectNode object) {
        V1AdmissionReviewRequest request = new V1AdmissionReviewRequest();
        request.setOperation(operation);
        V1Kind kind = new V1Kind();
        kind.setKind(kindName);
        request.setKind(kind);
        if (object != null) {
            request.setObject(object);
        }
        return request;
    }

}
