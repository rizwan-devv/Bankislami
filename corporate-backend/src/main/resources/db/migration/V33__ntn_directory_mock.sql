-- Mock NTN corporate directory for Existing-customer registration path

CREATE TABLE ntn_directory (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    ntn_number           VARCHAR(50)  NOT NULL,
    legal_name           VARCHAR(255) NOT NULL,
    entity_type          VARCHAR(40)  NOT NULL,
    tax_country          VARCHAR(80)  NOT NULL DEFAULT 'Pakistan',
    registered_address   VARCHAR(500),
    nature_of_business   VARCHAR(255),
    contact_phone        VARCHAR(30),
    contact_email        VARCHAR(120),
    UNIQUE (ntn_number)
);

CREATE TABLE ntn_accounts (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    ntn_directory_id  BIGINT       NOT NULL,
    account_no        VARCHAR(40)  NOT NULL,
    account_title     VARCHAR(255) NOT NULL,
    iban              VARCHAR(40),
    account_type      VARCHAR(40)  NOT NULL DEFAULT 'CURRENT',
    currency          VARCHAR(10)  NOT NULL DEFAULT 'PKR',
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_ntn_acc_dir FOREIGN KEY (ntn_directory_id) REFERENCES ntn_directory(id)
);

INSERT INTO ntn_directory (ntn_number, legal_name, entity_type, registered_address, nature_of_business, contact_phone, contact_email) VALUES
('1234567-8', 'Al-Noor Trading Concern', 'SOLE_PROPRIETORSHIP', 'Plot 12, SITE, Karachi', 'Wholesale trading', '03001234567', 'owner@alnoor.example'),
('9876543-1', 'Barakah Partners', 'PARTNERSHIP', 'Office 4, Blue Area, Islamabad', 'Consulting', '03009876543', 'partners@barakah.example'),
('5555555-5', 'Green Horizon Pvt Ltd', 'LIMITED_COMPANY', 'Clifton Block 4, Karachi', 'Import/Export', '03215555555', 'corp@greenhorizon.example');

INSERT INTO ntn_accounts (ntn_directory_id, account_no, account_title, iban, account_type) VALUES
(1, '1002003001', 'Al-Noor Trading Concern', 'PK36BIPL0000001002003001', 'CURRENT'),
(1, '1002003002', 'Al-Noor Trading - Ops', 'PK36BIPL0000001002003002', 'CURRENT'),
(2, '2003004001', 'Barakah Partners', 'PK36BIPL0000002003004001', 'CURRENT'),
(2, '2003004002', 'Barakah Partners - Payroll', 'PK36BIPL0000002003004002', 'CURRENT'),
(2, '2003004003', 'Barakah Partners - Collection', 'PK36BIPL0000002003004003', 'COLLECTION'),
(3, '3004005001', 'Green Horizon Pvt Ltd', 'PK36BIPL0000003004005001', 'CURRENT'),
(3, '3004005002', 'Green Horizon - Trade', 'PK36BIPL0000003004005002', 'CURRENT');
