package com.corporate.agent.web.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiResponse {

    private ApiResponse() {}

    public static Map<String, Object> ok(Object data) {
        return of("000", "SUCCESS", data);
    }

    public static Map<String, Object> of(String code, String messages, Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("responsecode", code);
        m.put("messages", messages);
        m.put("data", data);
        return m;
    }

    public static Map<String, Object> fail(String code, String messages) {
        return of(code, messages, null);
    }
}
