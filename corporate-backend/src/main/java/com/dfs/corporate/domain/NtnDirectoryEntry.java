package com.dfs.corporate.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ntn_directory")
public class NtnDirectoryEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ntn_number", nullable = false, unique = true, length = 50)
    private String ntnNumber;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "tax_country", nullable = false, length = 80)
    private String taxCountry = "Pakistan";

    @Column(name = "registered_address", length = 500)
    private String registeredAddress;

    @Column(name = "nature_of_business")
    private String natureOfBusiness;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "contact_email", length = 120)
    private String contactEmail;

    @OneToMany(mappedBy = "directory", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private List<NtnDirectoryAccount> accounts = new ArrayList<>();

    public Long getId() { return id; }
    public String getNtnNumber() { return ntnNumber; }
    public String getLegalName() { return legalName; }
    public String getEntityType() { return entityType; }
    public String getTaxCountry() { return taxCountry; }
    public String getRegisteredAddress() { return registeredAddress; }
    public String getNatureOfBusiness() { return natureOfBusiness; }
    public String getContactPhone() { return contactPhone; }
    public String getContactEmail() { return contactEmail; }
    public List<NtnDirectoryAccount> getAccounts() { return accounts; }
}
