-- Dev reset: удалить операционные данные, оставить справочники и учётки.
-- После миграции: airline, aircraft_type, gate, app_user — без schedule/flight.

DELETE FROM delay_warning;
DELETE FROM gate_assignment;
DELETE FROM flight;
DELETE FROM schedule_slot;
DELETE FROM schedule;
