-- Pre-approval customer acknowledgment (secure email link before back-office approve)
ALTER TABLE parties ADD COLUMN IF NOT EXISTS acknowledgment_token VARCHAR(64);
ALTER TABLE parties ADD COLUMN IF NOT EXISTS acknowledgment_sent_at TIMESTAMP;
ALTER TABLE parties ADD COLUMN IF NOT EXISTS acknowledgment_expires_at TIMESTAMP;
ALTER TABLE parties ADD COLUMN IF NOT EXISTS acknowledged_at TIMESTAMP;
ALTER TABLE parties ADD COLUMN IF NOT EXISTS acknowledgment_ip VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_parties_acknowledgment_token ON parties (acknowledgment_token);
