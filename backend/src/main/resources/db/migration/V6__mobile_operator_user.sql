-- Тестовый пользователь мобильного клиента (оперативный персонал): READ_ONLY, BCrypt.
-- Регистрации в ТЗ нет: учётки вносятся администратором / миграцией.
-- password_hash: DelegatingPasswordEncoder — префикс {bcrypt} обязателен.

INSERT INTO app_user (username, password_hash, role) VALUES
    (
        'mobile_operator_daun228',
        '{bcrypt}$2a$12$i.0OZtgJJQm9V8EaU1rZZeGkD7bwpgFRsVejE5seg3Kujmet4OqAK',
        'READ_ONLY'
    )
ON CONFLICT (username) DO NOTHING;
