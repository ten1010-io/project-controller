package io.ten1010.aipub.projectcontroller.mutating.service;

import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Optional;

@AllArgsConstructor
public class CompositeReviewHandler implements ReviewHandler {

    private final List<ReviewHandler> handlers;

    @Override
    public void handle(V1AdmissionReview review) {
        ReviewHandler handler = getHandler(review).orElseThrow();
        handler.handle(review);
    }

    @Override
    public boolean canHandle(V1AdmissionReview review) {
        return getHandler(review).isPresent();
    }

    private Optional<ReviewHandler> getHandler(V1AdmissionReview review) {
        for (ReviewHandler handler : this.handlers) {
            if (handler.canHandle(review)) {
                return Optional.of(handler);
            }
        }
        return Optional.empty();
    }

}
