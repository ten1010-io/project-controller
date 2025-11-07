package io.ten1010.aipub.projectcontroller.controller;

import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.ApiException;
import java.net.HttpURLConnection;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractReconciler implements Reconciler {

  private final Logger logger;
  @Getter
  @Setter
  private ReconcileRequestLogMessageFactory logMessageFactory;
  @Getter
  @Setter
  private Duration apiConflictFailRequeueDuration;
  @Getter
  @Setter
  private Duration generalFailRequeueDuration;

  public AbstractReconciler() {
    this.logger = LoggerFactory.getLogger(getClass());
    this.logMessageFactory = new ReconcileRequestLogMessageFactory();
    this.apiConflictFailRequeueDuration = Duration.ofSeconds(5);
    this.generalFailRequeueDuration = Duration.ofSeconds(60);
  }

  @Override
  public Result reconcile(Request request) {
    try {
      return reconcileInternal(request);
    } catch (Exception e) {
      if (e instanceof ApiException apiException) {
        if (apiException.getCode() == HttpURLConnection.HTTP_CONFLICT) {
          this.logger.info(this.logMessageFactory.createMessage(request, e, true));
          return new Result(true, getApiConflictFailRequeueDuration());
        }
      }
      this.logger.error(this.logMessageFactory.createMessage(request, e, true));
      return new Result(true, getGeneralFailRequeueDuration());
    }
  }

  protected abstract Result reconcileInternal(Request request) throws ApiException;

}
