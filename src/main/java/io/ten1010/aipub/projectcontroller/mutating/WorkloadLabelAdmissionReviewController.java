package io.ten1010.aipub.projectcontroller.mutating;

import static io.ten1010.aipub.projectcontroller.mutating.WorkloadLabelAdmissionReviewController.PATH;

import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.service.WorkloadLabelReviewHandler;
import lombok.extern.slf4j.Slf4j;
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

  private final WorkloadLabelReviewHandler handler;

  public WorkloadLabelAdmissionReviewController(WorkloadLabelReviewHandler handler) {
    this.handler = handler;
  }

  @PostMapping(value = "", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<V1AdmissionReview> create(@RequestBody V1AdmissionReview review) {
    log.debug("Workload label admission review request received");
    V1AdmissionReview clone = V1AdmissionReviewUtils.clone(review);

    if (clone.getRequest() == null) {
      throw new IllegalPropertyException(AdmissionReviewConstants.REVIEW_OBJECT_NAME,
          "/request", "request is null");
    }

    if (this.handler.canHandle(clone)) {
      this.handler.handle(clone);
    } else {
      V1AdmissionReviewUtils.allow(clone);
    }

    return ResponseEntity.ok(clone);
  }

}
