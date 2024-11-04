package io.ten1010.aipub.projectcontroller.domain;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

public class ExceptionLogMessageFactory {

    private static String createBasicExceptionLogMessage(Exception e) {
        return "Exception class : " + e.getClass().getSimpleName() +
                "\n" +
                "Exception message : " + e.getMessage();
    }

    private static String createStackTraceLogMessage(Exception e) {
        Writer strWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(strWriter);
        e.printStackTrace(printWriter);
        printWriter.close();

        return strWriter.toString();
    }

    public String createExceptionLogMessage(Exception e, boolean includeStackTrace) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(createBasicExceptionLogMessage(e));
        if (includeStackTrace) {
            stringBuilder.append("\n");
            stringBuilder.append(createStackTraceLogMessage(e));
        }

        return stringBuilder.toString();
    }

}
