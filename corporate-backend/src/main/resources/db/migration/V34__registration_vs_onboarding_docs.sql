-- Split document catalogs and KYC flags: REGISTRATION vs ONBOARDING (independently configurable).

ALTER TABLE entity_onboarding_documents
    ADD COLUMN phase VARCHAR(20) NOT NULL DEFAULT 'ONBOARDING' AFTER entity_type;

ALTER TABLE entity_onboarding_documents
    DROP INDEX uq_entity_onboarding_doc;

ALTER TABLE entity_onboarding_documents
    ADD CONSTRAINT uq_entity_onboarding_doc UNIQUE (entity_type, phase, document_code);

UPDATE entity_onboarding_documents SET phase = 'ONBOARDING' WHERE phase IS NULL OR phase = '' OR phase = 'ONBOARDING';

CREATE INDEX idx_entity_docs_phase ON entity_onboarding_documents (entity_type, phase, active, sort_order);

ALTER TABLE entity_kyc_policy
    ADD COLUMN registration_kyc_required BOOLEAN NOT NULL DEFAULT TRUE AFTER kyc_required,
    ADD COLUMN onboarding_kyc_required BOOLEAN NOT NULL DEFAULT TRUE AFTER registration_kyc_required;

UPDATE entity_kyc_policy
SET registration_kyc_required = kyc_required,
    onboarding_kyc_required = kyc_required;

-- Light default registration pack (admin can edit / add / remove custom rows).
INSERT INTO entity_onboarding_documents
    (entity_type, phase, document_code, document_label, mandatory, requirement, one_of_group, active, sort_order)
VALUES
('SOLE_PROPRIETORSHIP', 'REGISTRATION', 'REG_NTN_CERT', 'NTN / tax registration certificate', TRUE, 'REQUIRED', 0, TRUE, 1),
('SMALL_BUSINESS', 'REGISTRATION', 'REG_NTN_CERT', 'NTN / tax registration certificate', TRUE, 'REQUIRED', 0, TRUE, 1),
('PARTNERSHIP', 'REGISTRATION', 'REG_NTN_CERT', 'NTN / tax registration certificate', TRUE, 'REQUIRED', 0, TRUE, 1),
('LLP', 'REGISTRATION', 'REG_NTN_CERT', 'NTN / tax registration certificate', TRUE, 'REQUIRED', 0, TRUE, 1),
('LIMITED_COMPANY', 'REGISTRATION', 'REG_NTN_CERT', 'NTN / tax registration certificate', TRUE, 'REQUIRED', 0, TRUE, 1),
('FOREIGN_BRANCH', 'REGISTRATION', 'REG_NTN_CERT', 'NTN / tax registration certificate', TRUE, 'REQUIRED', 0, TRUE, 1),
('TRUST_SOCIETY', 'REGISTRATION', 'REG_NTN_CERT', 'NTN / tax registration certificate', TRUE, 'REQUIRED', 0, TRUE, 1),
('NGO_NPO', 'REGISTRATION', 'REG_NTN_CERT', 'NTN / tax registration certificate', TRUE, 'REQUIRED', 0, TRUE, 1);
