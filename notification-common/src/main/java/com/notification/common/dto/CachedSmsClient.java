package com.notification.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class CachedSmsClient {
    private Map<String, Object> config;
    private String configHash;
}
