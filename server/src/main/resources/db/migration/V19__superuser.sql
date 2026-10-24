-- Суперпользователь system: видит все кампании, но не появляется в участниках.
ALTER TABLE users ADD COLUMN IF NOT EXISTS superuser BOOLEAN NOT NULL DEFAULT FALSE;
