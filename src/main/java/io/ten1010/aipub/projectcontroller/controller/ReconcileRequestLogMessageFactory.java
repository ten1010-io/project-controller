package io.ten1010.aipub.projectcontroller.controller;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.ten1010.aipub.projectcontroller.domain.ExceptionLogMessageFactory;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Function;

public class ReconcileRequestLogMessageFactory {

    private static String createExceptionOccurredMessage(String requestDescription) {
        return String.format("Exception occurred Processing reconcile request [%s]", requestDescription);
    }

    private static String createRequestDescription(Request request) {
        return String.format("namespace=%s name=%s", request.getNamespace(), request.getName());
    }

    private final ExceptionLogMessageFactory exceptionLogMessageFactory;
    @Getter
    @Setter
    private Function<Request, String> requestDescriptionFactory;

    public ReconcileRequestLogMessageFactory() {
        this.exceptionLogMessageFactory = new ExceptionLogMessageFactory();
        this.requestDescriptionFactory = ReconcileRequestLogMessageFactory::createRequestDescription;
    }

    public String createMessage(Request request, Exception e, boolean includeStackTrace) {
        String requestDescription = this.requestDescriptionFactory.apply(request);
        return createExceptionOccurredMessage(requestDescription) +
                "\n" +
                this.exceptionLogMessageFactory.createExceptionLogMessage(e, includeStackTrace);
    }

}
