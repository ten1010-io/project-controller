package io.ten1010.aipub.projectcontroller.mutating;

import static io.ten1010.aipub.projectcontroller.mutating.WorkloadLabelAdmissionReviewController.PATH;

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
public class WorkloadLabelAdmissionReviewController {

  public static final String PATH = "/api/v1/workloadlabel/mutate";

  private final List<ReviewHandler> reviewHandlers;

  public WorkloadLabelAdmissionReviewController(
      @Qualifier("workloadLabelReviewHandlers") List<ReviewHandler> reviewHandlers) {
    this.reviewHandlers = reviewHandlers;
  }

  @PostMapping(value = "", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<V1AdmissionReview> create(@RequestBody V1AdmissionReview review) {
    log.debug("Workload label admission review request received");
    V1AdmissionReview clone = V1AdmissionReviewUtils.clone(review);

    if (clone.getRequest() == null) {
      throw new IllegalPropertyException(AdmissionReviewConstants.REVIEW_OBJECT_NAME,
          "/request", "request is null");
    }

    boolean handled = false;
    for (ReviewHandler handler : this.reviewHandlers) {
      if (handler.canHandle(clone)) {
        handler.handle(clone);
        handled = true;
        if (clone.getResponse() != null && !Boolean.TRUE.equals(clone.getResponse().getAllowed())) {
          break;
        }
      }
    }

    if (!handled) {
      V1AdmissionReviewUtils.allowMerging(clone);
    }

    return ResponseEntity.ok(clone);
  }

}
