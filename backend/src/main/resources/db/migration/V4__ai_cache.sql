CREATE TABLE ai_explanation (
    cache_key   VARCHAR(64)  PRIMARY KEY,          -- sha256 of kind + facts sent to the model
    kind        VARCHAR(16)  NOT NULL,
    text        TEXT         NOT NULL,
    model       VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE merchant_category (
    name_key    VARCHAR(120) PRIMARY KEY,          -- normalized merchant name
    category    VARCHAR(64)  NOT NULL,
    source      VARCHAR(16)  NOT NULL,             -- AI | RULES
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
