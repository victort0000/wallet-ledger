CREATE TABLE wallets
(
    id         UUID           NOT NULL,
    player_id  VARCHAR(255)   NOT NULL,
    balance    DECIMAL(18, 4) NOT NULL,
    currency   VARCHAR(3)     NOT NULL,
    version    BIGINT,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_wallets PRIMARY KEY (id)
);

ALTER TABLE wallets
    ADD CONSTRAINT uc_wallets_playerid UNIQUE (player_id);