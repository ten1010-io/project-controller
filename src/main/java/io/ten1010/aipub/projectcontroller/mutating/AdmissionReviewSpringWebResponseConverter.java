package io.ten1010.aipub.projectcontroller.mutating;

import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.common.eh.web.WebResponse;
import io.ten1010.common.eh.web.spring.DefaultSpringWebResponseConverter;
import io.ten1010.common.eh.web.spring.SpringWebResponseConverter;
import org.springframework.http.ResponseEntity;

public class AdmissionReviewSpringWebResponseConverter implements SpringWebResponseConverter {

    private final SpringWebResponseConverter converter = new DefaultSpringWebResponseConverter();

    @Override
    public ResponseEntity<Object> convert(WebResponse response) {
        if (response.getException() instanceof AdmissionReviewException reviewException) {
            V1AdmissionReview review = reviewException.getReview();
            int status = response.getHttpStatus();

            V1AdmissionReviewUtils.reject(review, status, response.getBody().getType());

            return ResponseEntity.ok(review);
        }
        return this.converter.convert(response);
    }

}
