package com.notification.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class JsonUtil {

    private static final ObjectMapper defaultMapper = new ObjectMapper();

    private static final ObjectMapper javaTimeAwareMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Returns the default ObjectMapper (no JavaTimeModule).
     */
    public static ObjectMapper defaultMapper() {
        return defaultMapper;
    }

    /**
     * Returns ObjectMapper configured to handle Java 8 Date/Time types.
     */
    public static ObjectMapper javaTimeMapper() {
        return javaTimeAwareMapper;
    }

    /**
     * Serializes object using default ObjectMapper.
     */
    public static String toJson(Object obj) {
        try {
            return defaultMapper().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing object to JSON", e);
        }
    }

    /**
     * Serializes object using JavaTime-aware ObjectMapper.
     */
    public static String toJsonWithJavaTime(Object obj) {
        try {
            return javaTimeMapper().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing object to JSON with JavaTime", e);
        }
    }

    /**
     * Deserializes JSON using default ObjectMapper.
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return defaultMapper().readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing JSON to object", e);
        }
    }

    /**
     * Deserializes JSON using JavaTime-aware ObjectMapper.
     */
    public static <T> T fromJsonWithJavaTime(String json, Class<T> clazz) {
        try {
            return javaTimeMapper().readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing JSON to object with JavaTime", e);
        }
    }

}
