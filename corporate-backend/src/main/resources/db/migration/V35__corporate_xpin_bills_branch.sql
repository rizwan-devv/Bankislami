-- Corporate XPin + bill upload; branch verify / pay-bill (no wallet deduct)

CREATE TABLE corporate_xpins (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id       VARCHAR(36)  NOT NULL UNIQUE,
    party_id        BIGINT       NOT NULL,
    pin_code        VARCHAR(16)  NOT NULL,
    person_name     VARCHAR(200) NOT NULL,
    phone           VARCHAR(40)  NOT NULL,
    email           VARCHAR(200) NOT NULL,
    amount          DECIMAL(18,2) NOT NULL,
    valid_until     TIMESTAMP    NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(200),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    consumed_at     TIMESTAMP    NULL,
    CONSTRAINT fk_xpin_party FOREIGN KEY (party_id) REFERENCES parties(id),
    INDEX idx_xpin_pin (pin_code),
    INDEX idx_xpin_party (party_id)
);

CREATE TABLE corporate_bills (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id               VARCHAR(36)  NOT NULL UNIQUE,
    party_id                BIGINT       NOT NULL,
    consumer_no             VARCHAR(64)  NOT NULL,
    consumer_name           VARCHAR(200) NOT NULL,
    amount                  DECIMAL(18,2) NOT NULL,
    amount_after_due_date   DECIMAL(18,2),
    due_date                DATE,
    batch_id                VARCHAR(36)  NOT NULL,
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by              VARCHAR(200),
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bill_party FOREIGN KEY (party_id) REFERENCES parties(id),
    INDEX idx_bill_party (party_id),
    INDEX idx_bill_consumer (party_id, consumer_no)
);

CREATE TABLE branch_bill_actions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id       VARCHAR(36)  NOT NULL UNIQUE,
    branch_account_id BIGINT     NOT NULL,
    party_id        BIGINT       NOT NULL,
    bill_id         BIGINT       NULL,
    xpin_id         BIGINT       NULL,
    action_type     VARCHAR(32)  NOT NULL,
    consumer_no     VARCHAR(64),
    amount          DECIMAL(18,2),
    notes           VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bba_party FOREIGN KEY (party_id) REFERENCES parties(id),
    INDEX idx_bba_branch (branch_account_id)
);
