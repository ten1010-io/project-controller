package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import java.util.List;
import java.util.Map;

public class LabelUtils {

    private static final String DELIMITER = "#";

    public static String getLabelString(String key, String value) {
        return key + DELIMITER + value;
    }

    public static List<String> getLabelStrings(Map<String, String> labels) {
        return labels.entrySet().stream()
                .map(e -> getLabelString(e.getKey(), e.getValue()))
                .toList();
    }

    public static String getKeyOfLabelString(String labelString) {
        String[] tokens = labelString.split(DELIMITER);
        if (tokens.length != 2) {
            throw new IllegalArgumentException();
        }

        return tokens[0];
    }

    public static String getValueOfLabelString(String labelString) {
        String[] tokens = labelString.split(DELIMITER);
        if (tokens.length != 2) {
            throw new IllegalArgumentException();
        }

        return tokens[1];
    }

}
