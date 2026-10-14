package com.dfs.corporate.web.dto;

import java.math.BigDecimal;

public record BranchPayBillResponse(
        String publicId,
        String action,
        String companyName,
        String consumerNo,
        String consumerName,
        BigDecimal amount,
        String message
) {}
