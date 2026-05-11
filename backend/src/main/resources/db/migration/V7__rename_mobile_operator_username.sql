-- Переименование тестового READ_ONLY пользователя мобильного клиента
UPDATE app_user SET username = 'mobile_operator' WHERE username = 'mobile_operator_daun228';
