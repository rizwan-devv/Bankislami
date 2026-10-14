package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;

public class XpinVerifyRequest {
    @NotBlank
    private String xpin;

    public String getXpin() { return xpin; }
    public void setXpin(String xpin) { this.xpin = xpin; }
}
