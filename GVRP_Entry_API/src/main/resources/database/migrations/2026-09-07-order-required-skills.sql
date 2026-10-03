ALTER TABLE orders
    ADD COLUMN required_skills JSON NULL
        COMMENT 'Skills a serving vehicle must provide'
        AFTER delivery_notes;
