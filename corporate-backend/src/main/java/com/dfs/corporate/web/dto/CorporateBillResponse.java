package com.dfs.corporate.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CorporateBillResponse(
        String publicId,
        String consumerNo,
        String consumerName,
        BigDecimal amount,
        BigDecimal amountAfterDueDate,
        LocalDate dueDate,
        String batchId,
        Instant createdAt
) {}
