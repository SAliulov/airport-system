-- Демо-данные для ручного теста API и фильтров (~10 строк в ключевых справочниках + рейсы).
-- Идемпотентность: ON CONFLICT по UNIQUE; schedule/flight — NOT EXISTS по маркеру flight_number DEMO-*.

-- ─── Airlines (до 10+ с учётом V1) ─────────────────────────────────────────
INSERT INTO airline (iata_code, name, country) VALUES
    ('BA', 'British Airways', 'UK'),
    ('LH', 'Lufthansa', 'Germany'),
    ('AF', 'Air France', 'France'),
    ('KL', 'KLM', 'Netherlands'),
    ('EY', 'Etihad', 'UAE'),
    ('QR', 'Qatar Airways', 'Qatar'),
    ('TK', 'Turkish Airlines', 'Turkey')
ON CONFLICT (iata_code) DO NOTHING;

-- ─── Aircraft types ───────────────────────────────────────────────────────
INSERT INTO aircraft_type (icao_code, passenger_capacity, size_category) VALUES
    ('E190', 100, 'NARROW'),
    ('E295', 132, 'NARROW'),
    ('B39M', 178, 'NARROW'),
    ('A21N', 200, 'NARROW'),
    ('B78X', 350, 'WIDE'),
    ('A359', 300, 'WIDE')
ON CONFLICT (icao_code) DO NOTHING;

-- ─── Gates ────────────────────────────────────────────────────────────────
INSERT INTO gate (gate_number, terminal, is_active, max_size_category) VALUES
    ('D1', 'D', true, 'NARROW'),
    ('D2', 'D', true, 'WIDE'),
    ('E1', 'E', true, 'NARROW'),
    ('E2', 'E', true, 'JUMBO'),
    ('F1', 'F', true, 'NARROW')
ON CONFLICT (gate_number) DO NOTHING;

-- ─── Schedules (маркер DEMO-01 … DEMO-10) ───────────────────────────────────
INSERT INTO schedule (flight_number, origin_airport, destination_airport, scheduled_departure, scheduled_arrival, airline_id)
SELECT 'DEMO-01', 'SVO', 'LED', TIMESTAMP '2026-05-01 06:00:00', TIMESTAMP '2026-05-01 08:00:00', airline_id FROM airline WHERE iata_code = 'SU'
    AND NOT EXISTS (SELECT 1 FROM schedule WHERE flight_number = 'DEMO-01');
INSERT INTO schedule (flight_number, origin_airport, destination_airport, scheduled_departure, scheduled_arrival, airline_id)
SELECT 'DEMO-02', 'LED', 'SVO', TIMESTAMP '2026-05-01 10:00:00', TIMESTAMP '2026-05-01 12:00:00', airline_id FROM airline WHERE iata_code = 'S7'
    AND NOT EXISTS (SELECT 1 FROM schedule WHERE flight_number = 'DEMO-02');
INSERT INTO schedule (flight_number, origin_airport, destination_airport, scheduled_departure, scheduled_arrival, airline_id)
SELECT 'DEMO-03', 'SVO', 'JFK', TIMESTAMP '2026-05-02 14:00:00', TIMESTAMP '2026-05-02 22:00:00', airline_id FROM airline WHERE iata_code = 'U6'
    AND NOT EXISTS (SELECT 1 FROM schedule WHERE flight_number = 'DEMO-03');
INSERT INTO schedule (flight_number, origin_airport, destination_airport, scheduled_departure, scheduled_arrival, airline_id)
SELECT 'DEMO-04', 'JFK', 'SVO', TIMESTAMP '2026-05-03 08:00:00', TIMESTAMP '2026-05-03 20:00:00', airline_id FROM airline WHERE iata_code = 'BA'
    AND NOT EXISTS (SELECT 1 FROM schedule WHERE flight_number = 'DEMO-04');
INSERT INTO schedule (flight_number, origin_airport, destination_airport, scheduled_departure, scheduled_arrival, airline_id)
SELECT 'DEMO-05', 'SVO', 'MUC', TIMESTAMP '2026-05-10 09:00:00', TIMESTAMP '2026-05-10 11:30:00', airline_id FROM airline WHERE iata_code = 'LH'
    AND NOT EXISTS (SELECT 1 FROM schedule WHERE flight_number = 'DEMO-05');
INSERT INTO schedule (flight_number, origin_airport, destination_airport, scheduled_departure, scheduled_arrival, airline_id)
SELECT 'DEMO-06', 'MUC', 'SVO', TIMESTAMP '2026-05-11 07:00:00', TIMESTAMP '2026-05-11 11:00:00', airline_id FROM airline WHERE iata_code = 'AF'
    AND NOT EXISTS (SELECT 1 FROM schedule WHERE flight_number = 'DEMO-06');
INSERT INTO schedule (flight_number, origin_airport, destination_airport, scheduled_departure, scheduled_arrival, airline_id)
SELECT 'DEMO-07', 'AMS', 'SVO', TIMESTAMP '2026-05-15 16:00:00', TIMESTAMP '2026-05-15 20:00:00', airline_id FROM airline WHERE iata_code = 'KL'
    AND NOT EXISTS (SELECT 1 FROM schedule WHERE flight_number = 'DEMO-07');
INSERT INTO schedule (flight_number, origin_airport, destination_airport, scheduled_departure, scheduled_arrival, airline_id)
SELECT 'DEMO-08', 'SVO', 'AUH', TIMESTAMP '2026-05-20 01:00:00', TIMESTAMP '2026-05-20 08:00:00', airline_id FROM airline WHERE iata_code = 'EY'
    AND NOT EXISTS (SELECT 1 FROM schedule WHERE flight_number = 'DEMO-08');
INSERT INTO schedule (flight_number, origin_airport, destination_airport, scheduled_departure, scheduled_arrival, airline_id)
SELECT 'DEMO-09', 'DOH', 'SVO', TIMESTAMP '2026-05-21 22:00:00', TIMESTAMP '2026-05-22 04:00:00', airline_id FROM airline WHERE iata_code = 'QR'
    AND NOT EXISTS (SELECT 1 FROM schedule WHERE flight_number = 'DEMO-09');
INSERT INTO schedule (flight_number, origin_airport, destination_airport, scheduled_departure, scheduled_arrival, airline_id)
SELECT 'DEMO-10', 'IST', 'SVO', TIMESTAMP '2026-05-25 12:00:00', TIMESTAMP '2026-05-25 16:00:00', airline_id FROM airline WHERE iata_code = 'TK'
    AND NOT EXISTS (SELECT 1 FROM schedule WHERE flight_number = 'DEMO-10');

-- ─── Flights (по одному на DEMO-* schedule, разные статусы) ───────────────
INSERT INTO flight (schedule_id, status, aircraft_type_id)
SELECT s.schedule_id, 'SCHEDULED', (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'A320' LIMIT 1)
FROM schedule s WHERE s.flight_number = 'DEMO-01'
  AND NOT EXISTS (SELECT 1 FROM flight f WHERE f.schedule_id = s.schedule_id);
INSERT INTO flight (schedule_id, status, aircraft_type_id)
SELECT s.schedule_id, 'DELAYED', (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'B738' LIMIT 1)
FROM schedule s WHERE s.flight_number = 'DEMO-02'
  AND NOT EXISTS (SELECT 1 FROM flight f WHERE f.schedule_id = s.schedule_id);
INSERT INTO flight (schedule_id, status, aircraft_type_id)
SELECT s.schedule_id, 'DEPARTED', (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'B77W' LIMIT 1)
FROM schedule s WHERE s.flight_number = 'DEMO-03'
  AND NOT EXISTS (SELECT 1 FROM flight f WHERE f.schedule_id = s.schedule_id);
INSERT INTO flight (schedule_id, status, aircraft_type_id)
SELECT s.schedule_id, 'ARRIVED', (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'A388' LIMIT 1)
FROM schedule s WHERE s.flight_number = 'DEMO-04'
  AND NOT EXISTS (SELECT 1 FROM flight f WHERE f.schedule_id = s.schedule_id);
INSERT INTO flight (schedule_id, status, aircraft_type_id)
SELECT s.schedule_id, 'SCHEDULED', (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'E190' LIMIT 1)
FROM schedule s WHERE s.flight_number = 'DEMO-05'
  AND NOT EXISTS (SELECT 1 FROM flight f WHERE f.schedule_id = s.schedule_id);
INSERT INTO flight (schedule_id, status, aircraft_type_id)
SELECT s.schedule_id, 'CANCELLED', (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'E295' LIMIT 1)
FROM schedule s WHERE s.flight_number = 'DEMO-06'
  AND NOT EXISTS (SELECT 1 FROM flight f WHERE f.schedule_id = s.schedule_id);
INSERT INTO flight (schedule_id, status, aircraft_type_id)
SELECT s.schedule_id, 'SCHEDULED', (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'B39M' LIMIT 1)
FROM schedule s WHERE s.flight_number = 'DEMO-07'
  AND NOT EXISTS (SELECT 1 FROM flight f WHERE f.schedule_id = s.schedule_id);
INSERT INTO flight (schedule_id, status, aircraft_type_id)
SELECT s.schedule_id, 'SCHEDULED', (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'A21N' LIMIT 1)
FROM schedule s WHERE s.flight_number = 'DEMO-08'
  AND NOT EXISTS (SELECT 1 FROM flight f WHERE f.schedule_id = s.schedule_id);
INSERT INTO flight (schedule_id, status, aircraft_type_id)
SELECT s.schedule_id, 'DELAYED', (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'B78X' LIMIT 1)
FROM schedule s WHERE s.flight_number = 'DEMO-09'
  AND NOT EXISTS (SELECT 1 FROM flight f WHERE f.schedule_id = s.schedule_id);
INSERT INTO flight (schedule_id, status, aircraft_type_id)
SELECT s.schedule_id, 'SCHEDULED', (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'A359' LIMIT 1)
FROM schedule s WHERE s.flight_number = 'DEMO-10'
  AND NOT EXISTS (SELECT 1 FROM flight f WHERE f.schedule_id = s.schedule_id);

-- ─── Назначения гейтов и задержки (часть DEMO-рейсов) ─────────────────────
INSERT INTO gate_assignment (assigned_from, assigned_to, flight_id, gate_id)
SELECT TIMESTAMP '2026-05-01 05:30:00', TIMESTAMP '2026-05-01 08:30:00', f.flight_id, g.gate_id
FROM flight f
JOIN schedule s ON s.schedule_id = f.schedule_id AND s.flight_number = 'DEMO-01'
CROSS JOIN (SELECT gate_id FROM gate WHERE gate_number = 'A1' LIMIT 1) g
WHERE NOT EXISTS (SELECT 1 FROM gate_assignment ga WHERE ga.flight_id = f.flight_id);

INSERT INTO gate_assignment (assigned_from, assigned_to, flight_id, gate_id)
SELECT TIMESTAMP '2026-05-01 09:30:00', TIMESTAMP '2026-05-01 12:30:00', f.flight_id, g.gate_id
FROM flight f
JOIN schedule s ON s.schedule_id = f.schedule_id AND s.flight_number = 'DEMO-02'
CROSS JOIN (SELECT gate_id FROM gate WHERE gate_number = 'D1' LIMIT 1) g
WHERE NOT EXISTS (SELECT 1 FROM gate_assignment ga WHERE ga.flight_id = f.flight_id);

INSERT INTO delay_warning (delay_minutes, reason, flight_id)
SELECT 25, 'Ожидание слота', f.flight_id
FROM flight f
JOIN schedule s ON s.schedule_id = f.schedule_id AND s.flight_number = 'DEMO-02'
WHERE NOT EXISTS (SELECT 1 FROM delay_warning dw WHERE dw.flight_id = f.flight_id AND dw.delay_minutes = 25);
