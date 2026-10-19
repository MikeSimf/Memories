-- Кентавр: добавляется отдельной миграцией, потому что V4 уже применена на
-- серверах, а Flyway проверяет контрольную сумму применённых файлов.
INSERT INTO races (code, name, sort_order) VALUES
    ('centaur', 'Кентавр', 170)
ON CONFLICT (code) DO NOTHING;
