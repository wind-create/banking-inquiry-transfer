-- CUSTOMERS
ALTER TABLE customers
    RENAME COLUMN created_at TO created_on;

ALTER TABLE customers
    RENAME COLUMN updated_at TO updated_on;

ALTER TABLE customers
    ADD COLUMN created_by VARCHAR(150) NOT NULL DEFAULT 'SYSTEM',
    ADD COLUMN updated_by VARCHAR(150) NOT NULL DEFAULT 'SYSTEM';


-- ACCOUNTS
ALTER TABLE accounts
    RENAME COLUMN created_at TO created_on;

ALTER TABLE accounts
    RENAME COLUMN updated_at TO updated_on;

ALTER TABLE accounts
    ADD COLUMN created_by VARCHAR(150) NOT NULL DEFAULT 'SYSTEM',
    ADD COLUMN updated_by VARCHAR(150) NOT NULL DEFAULT 'SYSTEM';