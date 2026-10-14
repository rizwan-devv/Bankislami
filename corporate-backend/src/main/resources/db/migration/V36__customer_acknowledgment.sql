-- Pre-approval customer acknowledgment (separate table — parties row is already at MySQL size limit)
CREATE TABLE party_acknowledgments (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    party_id        BIGINT       NOT NULL,
    token           VARCHAR(64)  NULL,
    sent_at         TIMESTAMP    NULL,
    expires_at      TIMESTAMP    NULL,
    acknowledged_at TIMESTAMP    NULL,
    client_ip       VARCHAR(64)  NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NULL,
    CONSTRAINT uk_party_ack_party UNIQUE (party_id),
    CONSTRAINT uk_party_ack_token UNIQUE (token),
    CONSTRAINT fk_party_ack_party FOREIGN KEY (party_id) REFERENCES parties (id)
);

CREATE INDEX idx_party_ack_token ON party_acknowledgments (token);
