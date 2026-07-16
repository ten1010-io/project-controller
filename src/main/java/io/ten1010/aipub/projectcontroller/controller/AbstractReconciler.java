package io.ten1010.aipub.projectcontroller.controller;

import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.HttpURLConnection;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractReconciler implements Reconciler {

  private static final String RECONCILE_TIMER_NAME = "projectcontroller.reconcile.duration";

  // Reconciler 는 *ControllerFactory 에서 new 로 생성되는 POJO 라 스프링 DI 대상이 아니다.
  // MeterRegistry 는 기동 시 MetricsConfiguration 에서 1회 정적 주입한다(단일 인스턴스 컨트롤러).
  private static MeterRegistry meterRegistry;

  public static void setMeterRegistry(MeterRegistry meterRegistry) {
    AbstractReconciler.meterRegistry = meterRegistry;
  }

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
    long startNanos = System.nanoTime();
    String result = "success";
    try {
      return reconcileInternal(request);
    } catch (Exception e) {
      if (e instanceof ApiException apiException) {
        if (apiException.getCode() == HttpURLConnection.HTTP_CONFLICT) {
          result = "conflict";
          this.logger.info(this.logMessageFactory.createMessage(request, e, true));
          return new Result(true, getApiConflictFailRequeueDuration());
        }
      }
      result = "error";
      this.logger.error(this.logMessageFactory.createMessage(request, e, true));
      return new Result(true, getGeneralFailRequeueDuration());
    } finally {
      recordReconcileDuration(result, startNanos);
    }
  }

  private void recordReconcileDuration(String result, long startNanos) {
    MeterRegistry registry = meterRegistry;
    if (registry == null) {
      return;
    }
    Timer.builder(RECONCILE_TIMER_NAME)
        .description("Reconcile execution time per controller")
        .tag("controller", getClass().getSimpleName())
        .tag("result", result)
        .register(registry)
        .record(Duration.ofNanos(System.nanoTime() - startNanos));
  }

  protected abstract Result reconcileInternal(Request request) throws ApiException;

}
