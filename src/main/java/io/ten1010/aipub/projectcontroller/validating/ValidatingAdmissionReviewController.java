package io.ten1010.aipub.projectcontroller.validating;

import static io.ten1010.aipub.projectcontroller.validating.ValidatingAdmissionReviewController.PATH;

import io.ten1010.aipub.projectcontroller.mutating.AdmissionReviewConstants;
import io.ten1010.aipub.projectcontroller.mutating.IllegalPropertyException;
import io.ten1010.aipub.projectcontroller.mutating.V1AdmissionReviewUtils;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.service.ReviewHandler;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(PATH)
public class ValidatingAdmissionReviewController {

  public static final String PATH = "/api/v1/userrelationship/validate";

  private final List<ReviewHandler> validateHandlers;

  public ValidatingAdmissionReviewController(
      @Qualifier("validateHandlers") List<ReviewHandler> validateHandlers) {
    this.validateHandlers = validateHandlers;
  }

  @PostMapping(value = "", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<V1AdmissionReview> validate(@RequestBody V1AdmissionReview review) {
    log.debug("Validate admission review request received");
    V1AdmissionReview clone = V1AdmissionReviewUtils.clone(review);

    if (clone.getRequest() == null) {
      throw new IllegalPropertyException(AdmissionReviewConstants.REVIEW_OBJECT_NAME,
          "/request", "request is null");
    }

    boolean handled = false;
    for (ReviewHandler handler : this.validateHandlers) {
      if (handler.canHandle(clone)) {
        handler.handle(clone);
        handled = true;
        if (clone.getResponse() != null && !Boolean.TRUE.equals(clone.getResponse().getAllowed())) {
          break;
        }
      }
    }

    if (!handled) {
      V1AdmissionReviewUtils.allow(clone);
    }

    return ResponseEntity.ok(clone);
  }

}
