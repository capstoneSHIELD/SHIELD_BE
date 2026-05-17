CREATE TABLE IF NOT EXISTS consultation_dynamic_plan (
    id              UUID PRIMARY KEY,
    consultation_id UUID NOT NULL REFERENCES consultations(id),
    version         INT NOT NULL DEFAULT 1,
    case_type_l1    VARCHAR(50),
    case_type_l2    VARCHAR(50),
    case_type_l3    VARCHAR(50),
    plan_confidence DECIMAL(4,3),
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS dynamic_plan_slot (
    id                UUID PRIMARY KEY,
    plan_id           UUID NOT NULL REFERENCES consultation_dynamic_plan(id),
    slot_id           VARCHAR(100) NOT NULL,
    label             VARCHAR(200) NOT NULL,
    source            VARCHAR(30) NOT NULL,
    static_mapping_id VARCHAR(200),
    required          BOOLEAN NOT NULL DEFAULT FALSE,
    priority          INT NOT NULL,
    status            VARCHAR(30) NOT NULL,
    collected_value   TEXT,
    pending_value     TEXT,
    validation_hint   VARCHAR(50),
    question_text     TEXT,
    asked_at          TIMESTAMP,
    answered_at       TIMESTAMP,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dynamic_plan_consultation
    ON consultation_dynamic_plan(consultation_id);

CREATE INDEX IF NOT EXISTS idx_dynamic_plan_slot_plan_status
    ON dynamic_plan_slot(plan_id, status);

CREATE INDEX IF NOT EXISTS idx_dynamic_plan_slot_plan_priority
    ON dynamic_plan_slot(plan_id, priority);
