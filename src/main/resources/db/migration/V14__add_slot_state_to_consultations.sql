ALTER TABLE consultations
    ADD COLUMN IF NOT EXISTS slot_state jsonb;
