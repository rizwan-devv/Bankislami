package com.dfs.corporate.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "branch_bill_actions")
public class BranchBillAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "branch_account_id", nullable = false)
    private Long branchAccountId;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "bill_id")
    private Long billId;

    @Column(name = "xpin_id")
    private Long xpinId;

    @Column(name = "action_type", nullable = false, length = 32)
    private String actionType;

    @Column(name = "consumer_no", length = 64)
    private String consumerNo;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public Long getBranchAccountId() { return branchAccountId; }
    public void setBranchAccountId(Long branchAccountId) { this.branchAccountId = branchAccountId; }
    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }
    public Long getBillId() { return billId; }
    public void setBillId(Long billId) { this.billId = billId; }
    public Long getXpinId() { return xpinId; }
    public void setXpinId(Long xpinId) { this.xpinId = xpinId; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getConsumerNo() { return consumerNo; }
    public void setConsumerNo(String consumerNo) { this.consumerNo = consumerNo; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
