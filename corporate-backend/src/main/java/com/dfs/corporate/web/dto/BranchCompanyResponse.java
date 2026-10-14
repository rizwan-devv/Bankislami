package com.dfs.corporate.web.dto;

public record BranchCompanyResponse(
        String publicId,
        String name,
        String ntn,
        String email,
        long billCount
) {}
