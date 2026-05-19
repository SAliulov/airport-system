-- АОС Шереметьево (SVO): очистка транзакционных данных и консистентное демо-наполнение.
-- Справочники airline / aircraft_type сохраняются; добавляется Победа (DP) при отсутствии.

DELETE FROM delay_warning;
DELETE FROM gate_assignment;
DELETE FROM flight;
DELETE FROM schedule;
DELETE FROM gate;

INSERT INTO airline (iata_code, name, country) VALUES
    ('DP', 'Победа', 'Россия')
ON CONFLICT (iata_code) DO NOTHING;

-- Терминал B (внутренние): 101–105 NARROW, 106–110 WIDE
INSERT INTO gate (gate_number, terminal, is_active, max_size_category) VALUES
    ('101', 'B', true, 'NARROW'),
    ('102', 'B', true, 'NARROW'),
    ('103', 'B', true, 'NARROW'),
    ('104', 'B', true, 'NARROW'),
    ('105', 'B', true, 'NARROW'),
    ('106', 'B', true, 'WIDE'),
    ('107', 'B', true, 'WIDE'),
    ('108', 'B', true, 'WIDE'),
    ('109', 'B', true, 'WIDE'),
    ('110', 'B', true, 'WIDE'),
    ('111', 'B', true, 'JUMBO');

-- Терминал C (международные): 121–125 NARROW, 126–130 WIDE
INSERT INTO gate (gate_number, terminal, is_active, max_size_category) VALUES
    ('121', 'C', true, 'NARROW'),
    ('122', 'C', true, 'NARROW'),
    ('123', 'C', true, 'NARROW'),
    ('124', 'C', true, 'NARROW'),
    ('125', 'C', true, 'NARROW'),
    ('126', 'C', true, 'WIDE'),
    ('127', 'C', true, 'WIDE'),
    ('128', 'C', true, 'WIDE'),
    ('129', 'C', true, 'WIDE'),
    ('130', 'C', true, 'WIDE');

-- Расписание (все маршруты через SVO), даты относительно CURRENT_DATE
INSERT INTO schedule (flight_number, origin_airport, destination_airport, scheduled_departure, scheduled_arrival, airline_id)
SELECT 'SU-1002', 'SVO', 'AER',
       (CURRENT_DATE + INTERVAL '1 day') + TIME '08:30:00',
       (CURRENT_DATE + INTERVAL '1 day') + TIME '12:00:00',
       airline_id FROM airline WHERE iata_code = 'SU';

INSERT INTO schedule (flight_number, origin_airport, destination_airport, scheduled_departure, scheduled_arrival, airline_id)
SELECT 'DP-205', 'SVO', 'LED',
       CURRENT_DATE + TIME '14:15:00',
       CURRENT_DATE + TIME '15:45:00',
       airline_id FROM airline WHERE iata_code = 'DP';

INSERT INTO schedule (flight_number, origin_airport, destination_airport, scheduled_departure, scheduled_arrival, airline_id)
SELECT 'SU-1103', 'AER', 'SVO',
       CURRENT_DATE + TIME '13:00:00',
       CURRENT_DATE + TIME '16:30:00',
       airline_id FROM airline WHERE iata_code = 'SU';

INSERT INTO schedule (flight_number, origin_airport, destination_airport, scheduled_departure, scheduled_arrival, airline_id)
SELECT 'DP-206', 'LED', 'SVO',
       (CURRENT_DATE + INTERVAL '1 day') + TIME '17:00:00',
       (CURRENT_DATE + INTERVAL '1 day') + TIME '18:30:00',
       airline_id FROM airline WHERE iata_code = 'DP';

-- Рейсы: разные статусы, консистентные actual_* и гейты SVO
INSERT INTO flight (schedule_id, status, aircraft_type_id, actual_departure, actual_arrival)
SELECT s.schedule_id, 'SCHEDULED',
       (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'A320' LIMIT 1),
       NULL, NULL
FROM schedule s WHERE s.flight_number = 'SU-1002';

INSERT INTO flight (schedule_id, status, aircraft_type_id, actual_departure, actual_arrival)
SELECT s.schedule_id, 'DELAYED',
       (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'B738' LIMIT 1),
       NULL, NULL
FROM schedule s WHERE s.flight_number = 'DP-205';

-- Вылетевший рейс (вчерашний слот SU-1002)
INSERT INTO schedule (flight_number, origin_airport, destination_airport, scheduled_departure, scheduled_arrival, airline_id)
SELECT 'SU-1002D', 'SVO', 'AER',
       (CURRENT_DATE - INTERVAL '1 day') + TIME '08:30:00',
       (CURRENT_DATE - INTERVAL '1 day') + TIME '12:00:00',
       airline_id FROM airline WHERE iata_code = 'SU';

INSERT INTO flight (schedule_id, status, aircraft_type_id, actual_departure, actual_arrival)
SELECT s.schedule_id, 'DEPARTED',
       (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'A320' LIMIT 1),
       (CURRENT_DATE - INTERVAL '1 day') + TIME '08:35:00',
       NULL
FROM schedule s WHERE s.flight_number = 'SU-1002D';

INSERT INTO flight (schedule_id, status, aircraft_type_id, actual_departure, actual_arrival)
SELECT s.schedule_id, 'ARRIVED',
       (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'A320' LIMIT 1),
       NULL,
       CURRENT_DATE + TIME '16:28:00'
FROM schedule s WHERE s.flight_number = 'SU-1103';

INSERT INTO flight (schedule_id, status, aircraft_type_id, actual_departure, actual_arrival)
SELECT s.schedule_id, 'SCHEDULED',
       (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'B738' LIMIT 1),
       NULL, NULL
FROM schedule s WHERE s.flight_number = 'DP-206';

INSERT INTO schedule (flight_number, origin_airport, destination_airport, scheduled_departure, scheduled_arrival, airline_id)
SELECT 'DP-205C', 'SVO', 'LED',
       (CURRENT_DATE + INTERVAL '2 days') + TIME '14:15:00',
       (CURRENT_DATE + INTERVAL '2 days') + TIME '15:45:00',
       airline_id FROM airline WHERE iata_code = 'DP';

INSERT INTO flight (schedule_id, status, aircraft_type_id, actual_departure, actual_arrival)
SELECT s.schedule_id, 'CANCELLED',
       (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'B738' LIMIT 1),
       NULL, NULL
FROM schedule s WHERE s.flight_number = 'DP-205C';

-- Назначения гейтов (только гейты SVO)
INSERT INTO gate_assignment (assigned_from, assigned_to, flight_id, gate_id)
SELECT CURRENT_DATE + TIME '07:45:00', CURRENT_DATE + TIME '09:15:00', f.flight_id, g.gate_id
FROM flight f
JOIN schedule s ON s.schedule_id = f.schedule_id AND s.flight_number = 'SU-1002'
JOIN gate g ON g.gate_number = '101'
WHERE f.status = 'SCHEDULED';

INSERT INTO gate_assignment (assigned_from, assigned_to, flight_id, gate_id)
SELECT CURRENT_DATE + TIME '13:30:00', CURRENT_DATE + TIME '16:00:00', f.flight_id, g.gate_id
FROM flight f
JOIN schedule s ON s.schedule_id = f.schedule_id AND s.flight_number = 'DP-205'
JOIN gate g ON g.gate_number = '105'
WHERE f.status = 'DELAYED';

INSERT INTO gate_assignment (assigned_from, assigned_to, flight_id, gate_id)
SELECT (CURRENT_DATE - INTERVAL '1 day') + TIME '08:00:00',
       (CURRENT_DATE - INTERVAL '1 day') + TIME '09:30:00',
       f.flight_id, g.gate_id
FROM flight f
JOIN schedule s ON s.schedule_id = f.schedule_id AND s.flight_number = 'SU-1002D'
JOIN gate g ON g.gate_number = '106'
WHERE f.status = 'DEPARTED';

INSERT INTO gate_assignment (assigned_from, assigned_to, flight_id, gate_id)
SELECT CURRENT_DATE + TIME '15:45:00', CURRENT_DATE + TIME '17:15:00', f.flight_id, g.gate_id
FROM flight f
JOIN schedule s ON s.schedule_id = f.schedule_id AND s.flight_number = 'SU-1103'
JOIN gate g ON g.gate_number = '126'
WHERE f.status = 'ARRIVED';

INSERT INTO delay_warning (delay_minutes, reason, flight_id, created_at)
SELECT 20, 'Задержка по техническим причинам', f.flight_id, CURRENT_TIMESTAMP
FROM flight f
JOIN schedule s ON s.schedule_id = f.schedule_id AND s.flight_number = 'DP-205'
WHERE f.status = 'DELAYED';
