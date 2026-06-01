-- Диспетчер с кириллическим логином (test-credentials.md).
-- dispatcher_zhenya из V8 не изменяется.

INSERT INTO app_user (username, password_hash, role) VALUES
    (
        'Диспетчер_Женя1987',
        '{bcrypt}$2a$12$G51sxBUq7a8fi3Uep8Nwj.KABhgNBVJtAVGXX7h.yDXB4f8rdHr/G',
        'DISPATCHER'
    )
ON CONFLICT (username) DO UPDATE SET
    password_hash = EXCLUDED.password_hash,
    role = EXCLUDED.role;
