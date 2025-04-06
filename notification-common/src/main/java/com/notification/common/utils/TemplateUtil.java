package com.notification.common.utils;

import java.util.Map;

public class TemplateUtil {
    public static String resolveTemplateWithParams(String text, Map<String, Object> params) {
        if (text == null || params == null || params.isEmpty()) return text;

        String result = text;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            result = result.replace(placeholder, String.valueOf(entry.getValue()));
        }
        return result;
    }
}
