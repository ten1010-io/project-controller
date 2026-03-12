package io.ten1010.aipub.projectcontroller.mutating.service;

import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import java.util.List;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class CompositeReviewHandler implements ReviewHandler {

  private final List<ReviewHandler> handlers;

  @Override
  public void handle(V1AdmissionReview review) {
    boolean handled = false;
    for (ReviewHandler handler : this.handlers) {
      if (handler.canHandle(review)) {
        handler.handle(review);
        handled = true;
      }
    }
    if (!handled) {
      throw new java.util.NoSuchElementException("No handler found for review");
    }
  }

  @Override
  public boolean canHandle(V1AdmissionReview review) {
    return this.handlers.stream().anyMatch(h -> h.canHandle(review));
  }

}
