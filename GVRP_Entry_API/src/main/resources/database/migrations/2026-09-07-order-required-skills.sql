ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS required_skills JSON NULL
    COMMENT 'Skills a serving vehicle must provide'
    AFTER delivery_notes;
