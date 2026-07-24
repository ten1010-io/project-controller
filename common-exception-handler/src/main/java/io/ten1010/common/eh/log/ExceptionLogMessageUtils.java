package io.ten1010.common.eh.log;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ExceptionLogMessageUtils {
    public static String createStackTraceLogMessage(Exception e) {
        StringWriter strWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(strWriter);
        e.printStackTrace(printWriter);
        printWriter.close();
        return strWriter.toString();
    }
}
