-- DFS wallet attached to a branch login. Used as the agent on cash-in / cash-out.
ALTER TABLE accounts
    ADD COLUMN dfs_mobile VARCHAR(20) NULL,
    ADD COLUMN dfs_cnic VARCHAR(20) NULL,
    ADD COLUMN dfs_mpin VARCHAR(16) NULL,
    ADD COLUMN dfs_imei VARCHAR(64) NULL;
