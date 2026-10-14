-- H2: registration vs onboarding document packs + dual KYC flags.

ALTER TABLE entity_onboarding_documents ADD COLUMN phase VARCHAR(20) DEFAULT 'ONBOARDING' NOT NULL;

ALTER TABLE entity_onboarding_documents DROP CONSTRAINT IF EXISTS uq_entity_onboarding_doc;
ALTER TABLE entity_onboarding_documents ADD CONSTRAINT uq_entity_onboarding_doc UNIQUE (entity_type, phase, document_code);

UPDATE entity_onboarding_documents SET phase = 'ONBOARDING' WHERE phase IS NULL OR TRIM(phase) = '';

ALTER TABLE entity_kyc_policy ADD COLUMN registration_kyc_required BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE entity_kyc_policy ADD COLUMN onboarding_kyc_required BOOLEAN DEFAULT TRUE NOT NULL;

UPDATE entity_kyc_policy
SET registration_kyc_required = kyc_required,
    onboarding_kyc_required = kyc_required;

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
