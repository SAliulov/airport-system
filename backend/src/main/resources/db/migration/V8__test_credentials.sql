-- Учётные записи из test-credentials.md (BCrypt, DelegatingPasswordEncoder).
-- operator_eugene  — READ_ONLY (мобильный оператор)
-- dispatcher_zhenya — DISPATCHER

INSERT INTO app_user (username, password_hash, role) VALUES
    (
        'operator_eugene',
        '{bcrypt}$2a$12$OqYFt/bcOuyPj9deOdIPreOZJvj3PRtVBPSU4cxphidXmu2YR8DYa',
        'READ_ONLY'
    ),
    (
        'dispatcher_zhenya',
        '{bcrypt}$2a$12$GapINjMkW8.zQKMZNOEP7.RAnBE/bNCOXAcUy4taXyWOmWQrAh/sq',
        'DISPATCHER'
    )
ON CONFLICT (username) DO UPDATE SET
    password_hash = EXCLUDED.password_hash,
    role = EXCLUDED.role;
