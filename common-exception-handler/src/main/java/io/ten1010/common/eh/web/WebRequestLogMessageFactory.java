package io.ten1010.common.eh.web;

import io.ten1010.common.eh.log.ExceptionLogMessageUtils;
import io.ten1010.common.eh.log.LogMessageFactory;

public class WebRequestLogMessageFactory implements LogMessageFactory<WebRequest> {
    private static String createRequestFailedMsg(WebRequest request) {
        if (request.getAuthentication() == null) {
            return String.format("WebRequest[method=%s uri=%s clientAddr=%s] failed", request.getMethod(), request.getUri(), request.getClientAddr());
        }
        return String.format("WebRequest[method=%s uri=%s clientAddr=%s authentication=%s] failed", request.getMethod(), request.getUri(), request.getClientAddr(), request.getAuthentication());
    }

    @Override
    public String createMessage(WebRequest request, Exception e) {
        return WebRequestLogMessageFactory.createRequestFailedMsg(request) + "\n" + ExceptionLogMessageUtils.createStackTraceLogMessage(e);
    }
}
