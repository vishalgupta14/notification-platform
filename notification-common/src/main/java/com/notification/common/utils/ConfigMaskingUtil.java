package com.notification.common.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ConfigMaskingUtil {

    private static final Set<String> SENSITIVE_KEYS = Set.of("password", "authToken", "apiKey", "secret");

    public static Map<String, Object> maskConfig(Map<String, Object> original) {
        Map<String, Object> masked = new HashMap<>();
        original.forEach((k, v) -> {
            if (SENSITIVE_KEYS.contains(k) && v instanceof String val) {
                masked.put(k, mask(val));
            } else {
                masked.put(k, v);
            }
        });
        return masked;
    }

    private static String mask(String value) {
        if (value.length() <= 4) return "****";
        return "************" + value.substring(value.length() - 4);
    }
}
