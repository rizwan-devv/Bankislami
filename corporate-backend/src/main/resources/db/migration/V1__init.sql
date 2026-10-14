-- Bank Islami Corporate — separate schema (not shared with DFS Corporate)

CREATE TABLE entity_kyc_policy (
    entity_type   VARCHAR(40)  NOT NULL PRIMARY KEY,
    kyc_required  BOOLEAN      NOT NULL DEFAULT TRUE,
    display_label VARCHAR(120)
);

INSERT INTO entity_kyc_policy (entity_type, kyc_required, display_label) VALUES
('SOLE_PROPRIETORSHIP', FALSE, 'Sole Proprietorship'),
('PARTNERSHIP', TRUE, 'Partnership'),
('LIMITED_COMPANY', TRUE, 'Limited Company'),
('SMALL_BUSINESS', FALSE, 'Small Business');

CREATE TABLE required_documents (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type   VARCHAR(40)  NOT NULL,
    doc_code      VARCHAR(60)  NOT NULL,
    label         VARCHAR(200) NOT NULL,
    mandatory     BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order    INT          NOT NULL DEFAULT 0
);

INSERT INTO required_documents (entity_type, doc_code, label, mandatory, sort_order) VALUES
('SOLE_PROPRIETORSHIP', 'CNIC_FRONT', 'CNIC Front', TRUE, 1),
('SOLE_PROPRIETORSHIP', 'CNIC_BACK', 'CNIC Back', TRUE, 2),
('SOLE_PROPRIETORSHIP', 'NTN_CERTIFICATE', 'NTN Certificate', TRUE, 3),
('SOLE_PROPRIETORSHIP', 'PROOF_OF_BUSINESS', 'Proof of Business', TRUE, 4),
('PARTNERSHIP', 'PARTNERSHIP_DEED', 'Partnership Deed', TRUE, 1),
('PARTNERSHIP', 'AUTHORITY_LETTER', 'Authority Letter', TRUE, 2),
('PARTNERSHIP', 'NTN_CERTIFICATE', 'NTN Certificate', TRUE, 3),
('PARTNERSHIP', 'PARTNER_CNICS', 'Partners CNIC copies', TRUE, 4),
('LIMITED_COMPANY', 'SECP_CERT', 'SECP Certificate', TRUE, 1),
('LIMITED_COMPANY', 'MOA_AOA', 'MOA / AOA', TRUE, 2),
('LIMITED_COMPANY', 'NTN_CERTIFICATE', 'NTN Certificate', TRUE, 3),
('LIMITED_COMPANY', 'BOARD_RESOLUTION', 'Board Resolution', TRUE, 4),
('SMALL_BUSINESS', 'CNIC_FRONT', 'CNIC Front', TRUE, 1),
('SMALL_BUSINESS', 'CNIC_BACK', 'CNIC Back', TRUE, 2),
('SMALL_BUSINESS', 'NTN_CERTIFICATE', 'NTN Certificate', TRUE, 3);

-- Mock existing corporate directory (seeded NTN accounts)
CREATE TABLE ntn_directory (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    ntn_number      VARCHAR(50)  NOT NULL,
    legal_name      VARCHAR(255) NOT NULL,
    entity_type     VARCHAR(40)  NOT NULL,
    tax_country     VARCHAR(80)  NOT NULL DEFAULT 'Pakistan',
    registered_address VARCHAR(500),
    nature_of_business VARCHAR(255),
    contact_phone   VARCHAR(30),
    contact_email   VARCHAR(120),
    UNIQUE (ntn_number)
);

CREATE TABLE ntn_accounts (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    ntn_directory_id BIGINT NOT NULL,
    account_no      VARCHAR(40)  NOT NULL,
    account_title   VARCHAR(255) NOT NULL,
    iban            VARCHAR(40),
    account_type    VARCHAR(40)  NOT NULL DEFAULT 'CURRENT',
    currency        VARCHAR(10)  NOT NULL DEFAULT 'PKR',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_ntn_acc_dir FOREIGN KEY (ntn_directory_id) REFERENCES ntn_directory(id)
);

INSERT INTO ntn_directory (ntn_number, legal_name, entity_type, registered_address, nature_of_business, contact_phone, contact_email) VALUES
('1234567-8', 'Al-Noor Trading Concern', 'SOLE_PROPRIETORSHIP', 'Plot 12, SITE, Karachi', 'Wholesale trading', '03001234567', 'owner@alnoor.example'),
('9876543-1', 'Barakah Partners LLP', 'PARTNERSHIP', 'Office 4, Blue Area, Islamabad', 'Consulting', '03009876543', 'partners@barakah.example'),
('5555555-5', 'Green Horizon Pvt Ltd', 'LIMITED_COMPANY', 'Clifton Block 4, Karachi', 'Import/Export', '03215555555', 'corp@greenhorizon.example');

INSERT INTO ntn_accounts (ntn_directory_id, account_no, account_title, iban, account_type) VALUES
(1, '1002003001', 'Al-Noor Trading Concern', 'PK36BIPL0000001002003001', 'CURRENT'),
(1, '1002003002', 'Al-Noor Trading - Ops', 'PK36BIPL0000001002003002', 'CURRENT'),
(2, '2003004001', 'Barakah Partners LLP', 'PK36BIPL0000002003004001', 'CURRENT'),
(2, '2003004002', 'Barakah Partners - Payroll', 'PK36BIPL0000002003004002', 'CURRENT'),
(2, '2003004003', 'Barakah Partners - Collection', 'PK36BIPL0000002003004003', 'COLLECTION'),
(3, '3004005001', 'Green Horizon Pvt Ltd', 'PK36BIPL0000003004005001', 'CURRENT'),
(3, '3004005002', 'Green Horizon - Trade', 'PK36BIPL0000003004005002', 'CURRENT');

CREATE TABLE applications (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tracking_id         VARCHAR(40)  NOT NULL UNIQUE,
    path_type           VARCHAR(20)  NOT NULL, -- NEW | EXISTING
    status              VARCHAR(40)  NOT NULL, -- DRAFT, EMAIL_PENDING, SUBMITTED, KYC_PENDING, ACK_PENDING, PENDING_APPROVAL, ACTIVE, REJECTED
    ntn_number          VARCHAR(50),
    legal_name          VARCHAR(255),
    entity_type         VARCHAR(40),
    tax_country         VARCHAR(80),
    registered_address  VARCHAR(500),
    nature_of_business  VARCHAR(255),
    contact_name        VARCHAR(120),
    contact_phone       VARCHAR(30),
    contact_email       VARCHAR(120),
    selected_account_nos VARCHAR(500), -- comma-separated
    email_verified_at   TIMESTAMP,
    acknowledgment_at   TIMESTAMP,
    acknowledgment_text VARCHAR(1000),
    kyc_required        BOOLEAN      NOT NULL DEFAULT FALSE,
    kyc_completed       BOOLEAN      NOT NULL DEFAULT FALSE,
    admin_notes         VARCHAR(1000),
    reject_reason       VARCHAR(500),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE application_documents (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id  BIGINT       NOT NULL,
    doc_code        VARCHAR(60)  NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    content_type    VARCHAR(120),
    storage_ref     VARCHAR(500) NOT NULL,
    review_status   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    uploaded_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_app_doc FOREIGN KEY (application_id) REFERENCES applications(id)
);

CREATE TABLE otp_tokens (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id  BIGINT,
    email           VARCHAR(120) NOT NULL,
    purpose         VARCHAR(40)  NOT NULL, -- SIGNUP_CONFIRM
    otp_code        VARCHAR(10)  NOT NULL,
    link_token      VARCHAR(80)  NOT NULL UNIQUE,
    expires_at      TIMESTAMP    NOT NULL,
    used_at         TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE portal_users (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id      BIGINT,
    username            VARCHAR(40)  NOT NULL UNIQUE, -- phone
    phone               VARCHAR(30)  NOT NULL,
    email               VARCHAR(120),
    password_hash       VARCHAR(255) NOT NULL,
    must_change_password BOOLEAN     NOT NULL DEFAULT TRUE,
    role                VARCHAR(40)  NOT NULL DEFAULT 'PORTAL_USER', -- PORTAL_USER | ADMIN
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Admin user is created on startup by AdminBootstrap (admin / Admin@123)
