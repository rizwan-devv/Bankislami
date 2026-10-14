package com.dfs.corporate.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record XpinVerifyResponse(
        boolean valid,
        String pinCode,
        String personName,
        String phone,
        String email,
        BigDecimal amount,
        Instant validUntil,
        String companyPublicId,
        String companyName,
        String companyEmail,
        String companyPhone,
        String companyNtn,
        String message
) {}
