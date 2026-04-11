-- Учётные записи для JWT (роли DISPATCHER / READ_ONLY).
-- password_hash — DelegatingPasswordEncoder: сейчас {noop}пароль (dev).
-- Свой BCrypt: new BCryptPasswordEncoder().encode("пароль") → одна строка вида $2a$10$...
-- UPDATE app_user SET password_hash = '{bcrypt}$2a$10$...' WHERE username = 'dispatcher';

CREATE TABLE app_user (
    user_id   SERIAL PRIMARY KEY,
    username  VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role      VARCHAR(32)  NOT NULL
);

INSERT INTO app_user (username, password_hash, role) VALUES
    ('dispatcher', '{noop}dispatcher123', 'DISPATCHER'),
    ('reader', '{noop}reader123', 'READ_ONLY');
