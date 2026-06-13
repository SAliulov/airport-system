-- Учётные записи (BCrypt, DelegatingPasswordEncoder)

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
    ),
    (
        'Диспетчер_Женя1987',
        '{bcrypt}$2a$12$G51sxBUq7a8fi3Uep8Nwj.KABhgNBVJtAVGXX7h.yDXB4f8rdHr/G',
        'DISPATCHER'
    );