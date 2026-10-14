package com.dfs.corporate.web.dto;

public record BillUploadResult(String batchId, int imported, int skipped, String message) {}
