package com.dfs.corporate.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "ntn_accounts")
public class NtnDirectoryAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ntn_directory_id", nullable = false)
    private NtnDirectoryEntry directory;

    @Column(name = "account_no", nullable = false, length = 40)
    private String accountNo;

    @Column(name = "account_title", nullable = false)
    private String accountTitle;

    @Column(length = 40)
    private String iban;

    @Column(name = "account_type", nullable = false, length = 40)
    private String accountType = "CURRENT";

    @Column(nullable = false, length = 10)
    private String currency = "PKR";

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    public Long getId() { return id; }
    public String getAccountNo() { return accountNo; }
    public String getAccountTitle() { return accountTitle; }
    public String getIban() { return iban; }
    public String getAccountType() { return accountType; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
}
