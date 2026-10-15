package com.dfs.corporate.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(nullable = false, unique = true, length = 200)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountStatus status;

    @Column(name = "is_first_login", nullable = false)
    private boolean firstLogin = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Branch cash-in / cash-out agent wallet. Not used by merchant logins. */
    @Column(name = "dfs_mobile", length = 20)
    private String dfsMobile;

    @Column(name = "dfs_cnic", length = 20)
    private String dfsCnic;

    @Column(name = "dfs_mpin", length = 16)
    private String dfsMpin;

    @Column(name = "dfs_imei", length = 64)
    private String dfsImei;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public boolean isFirstLogin() { return firstLogin; }
    public void setFirstLogin(boolean firstLogin) { this.firstLogin = firstLogin; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public Instant getCreatedAt() { return createdAt; }
    public String getDfsMobile() { return dfsMobile; }
    public void setDfsMobile(String dfsMobile) { this.dfsMobile = dfsMobile; }
    public String getDfsCnic() { return dfsCnic; }
    public void setDfsCnic(String dfsCnic) { this.dfsCnic = dfsCnic; }
    public String getDfsMpin() { return dfsMpin; }
    public void setDfsMpin(String dfsMpin) { this.dfsMpin = dfsMpin; }
    public String getDfsImei() { return dfsImei; }
    public void setDfsImei(String dfsImei) { this.dfsImei = dfsImei; }
}
