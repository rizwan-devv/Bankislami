package com.corporate.agent.domain;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/** Locally managed agent (login / registration owned by this API). */
public class AgentAccount {

    private static final AtomicLong IDS = new AtomicLong(1000);

    private final long customerId;
    private final long accountId;
    private final long appUserId;
    private String mobile;
    private String passwordMpin;
    private String accountTitle;
    private String nidNo;
    private String imei;
    private String deviceModel;
    private String accountLevelCode = "L4";
    private double balance;
    private String iban;
    private Instant registeredAt = Instant.now();
    private boolean deviceVerified;

    public AgentAccount(String mobile, String passwordMpin, String accountTitle, String nidNo) {
        this.customerId = IDS.incrementAndGet();
        this.accountId = IDS.incrementAndGet();
        this.appUserId = IDS.incrementAndGet();
        this.mobile = mobile;
        this.passwordMpin = passwordMpin;
        this.accountTitle = accountTitle != null ? accountTitle : "Corporate Agent";
        this.nidNo = nidNo != null ? nidNo : "";
        this.balance = 10_000.0;
        this.iban = "PK00CORP" + mobile.replaceAll("\\D", "");
        this.deviceVerified = false;
    }

    public long getCustomerId() { return customerId; }
    public long getAccountId() { return accountId; }
    public long getAppUserId() { return appUserId; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getPasswordMpin() { return passwordMpin; }
    public void setPasswordMpin(String passwordMpin) { this.passwordMpin = passwordMpin; }
    public String getAccountTitle() { return accountTitle; }
    public void setAccountTitle(String accountTitle) { this.accountTitle = accountTitle; }
    public String getNidNo() { return nidNo; }
    public void setNidNo(String nidNo) { this.nidNo = nidNo; }
    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }
    public String getDeviceModel() { return deviceModel; }
    public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }
    public String getAccountLevelCode() { return accountLevelCode; }
    public void setAccountLevelCode(String accountLevelCode) { this.accountLevelCode = accountLevelCode; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
    public String getIban() { return iban; }
    public Instant getRegisteredAt() { return registeredAt; }
    public boolean isDeviceVerified() { return deviceVerified; }
    public void setDeviceVerified(boolean deviceVerified) { this.deviceVerified = deviceVerified; }
}
