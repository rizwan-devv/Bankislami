package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class GenerateXpinRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    @NotBlank
    @Size(max = 40)
    private String phone;

    @NotBlank
    @Email
    @Size(max = 200)
    private String email;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;

    /** Days from now until pin expires (1–365). */
    @NotNull
    @Min(1)
    @Max(365)
    private Integer validityDays;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Integer getValidityDays() { return validityDays; }
    public void setValidityDays(Integer validityDays) { this.validityDays = validityDays; }
}
