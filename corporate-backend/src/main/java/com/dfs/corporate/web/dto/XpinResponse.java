package com.dfs.corporate.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record XpinResponse(
        String publicId,
        String pinCode,
        String name,
        String phone,
        String email,
        BigDecimal amount,
        Instant validUntil,
        String status,
        Instant createdAt
) {}
