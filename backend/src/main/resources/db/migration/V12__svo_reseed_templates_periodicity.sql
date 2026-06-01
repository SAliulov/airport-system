-- SVO demo reseed: шаблоны с периодичностью + несколько экземпляров на шаблон.

DELETE FROM delay_warning;
DELETE FROM gate_assignment;
DELETE FROM flight;
DELETE FROM schedule_slot;
DELETE FROM schedule;

-- SU-1002: WEEKLY step=1, вт+сб
INSERT INTO schedule (flight_number, origin_airport, destination_airport, effective_from, is_active, periodicity_type, periodicity_step, airline_id)
SELECT 'SU-1002', 'SVO', 'AER', CURRENT_DATE, true, 'WEEKLY', 1, airline_id FROM airline WHERE iata_code = 'SU';

INSERT INTO schedule_slot (schedule_id, day_of_week, departure_time, arrival_time)
SELECT s.schedule_id, 2, TIME '08:30', TIME '12:00' FROM schedule s WHERE s.flight_number = 'SU-1002';
INSERT INTO schedule_slot (schedule_id, day_of_week, departure_time, arrival_time)
SELECT s.schedule_id, 6, TIME '08:30', TIME '12:00' FROM schedule s WHERE s.flight_number = 'SU-1002';

-- DP-205: INTERVAL каждые 3 дня
INSERT INTO schedule (flight_number, origin_airport, destination_airport, effective_from, is_active, periodicity_type, periodicity_step, airline_id)
SELECT 'DP-205', 'SVO', 'LED', CURRENT_DATE, true, 'INTERVAL', 3, airline_id FROM airline WHERE iata_code = 'DP';

INSERT INTO schedule_slot (schedule_id, day_of_week, departure_time, arrival_time)
SELECT s.schedule_id, NULL, TIME '14:15', TIME '15:45' FROM schedule s WHERE s.flight_number = 'DP-205';

-- SU-1103: WEEKLY step=2, среда
INSERT INTO schedule (flight_number, origin_airport, destination_airport, effective_from, is_active, periodicity_type, periodicity_step, airline_id)
SELECT 'SU-1103', 'AER', 'SVO', CURRENT_DATE, true, 'WEEKLY', 2, airline_id FROM airline WHERE iata_code = 'SU';

INSERT INTO schedule_slot (schedule_id, day_of_week, departure_time, arrival_time)
SELECT s.schedule_id, 3, TIME '13:00', TIME '16:30' FROM schedule s WHERE s.flight_number = 'SU-1103';

-- DP-206: WEEKLY step=1, ежедневный слот (INTERVAL step=1 экв., но WEEKLY с одним DOW)
INSERT INTO schedule (flight_number, origin_airport, destination_airport, effective_from, is_active, periodicity_type, periodicity_step, airline_id)
SELECT 'DP-206', 'LED', 'SVO', CURRENT_DATE, true, 'WEEKLY', 1, airline_id FROM airline WHERE iata_code = 'DP';

INSERT INTO schedule_slot (schedule_id, day_of_week, departure_time, arrival_time)
SELECT s.schedule_id,
       CASE WHEN EXTRACT(DOW FROM (CURRENT_DATE + INTERVAL '1 day'))::int = 0 THEN 7
            ELSE EXTRACT(DOW FROM (CURRENT_DATE + INTERVAL '1 day'))::int END,
       TIME '17:00', TIME '18:30'
FROM schedule s WHERE s.flight_number = 'DP-206';

-- Экземпляры рейсов
INSERT INTO flight (schedule_id, slot_id, operation_date, scheduled_departure, scheduled_arrival, status, aircraft_type_id)
SELECT s.schedule_id, sl.slot_id, CURRENT_DATE + ((2 - EXTRACT(DOW FROM CURRENT_DATE)::int + 7) % 7) * INTERVAL '1 day',
       (CURRENT_DATE + ((2 - EXTRACT(DOW FROM CURRENT_DATE)::int + 7) % 7) * INTERVAL '1 day') + TIME '08:30',
       (CURRENT_DATE + ((2 - EXTRACT(DOW FROM CURRENT_DATE)::int + 7) % 7) * INTERVAL '1 day') + TIME '12:00',
       'SCHEDULED', (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'A320' LIMIT 1)
FROM schedule s
JOIN schedule_slot sl ON sl.schedule_id = s.schedule_id AND sl.day_of_week = 2
WHERE s.flight_number = 'SU-1002';

INSERT INTO flight (schedule_id, slot_id, operation_date, scheduled_departure, scheduled_arrival, status, aircraft_type_id)
SELECT s.schedule_id, sl.slot_id, CURRENT_DATE,
       CURRENT_DATE + TIME '14:15', CURRENT_DATE + TIME '15:45',
       'DELAYED', (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'B738' LIMIT 1)
FROM schedule s
JOIN schedule_slot sl ON sl.schedule_id = s.schedule_id
WHERE s.flight_number = 'DP-205';

INSERT INTO flight (schedule_id, slot_id, operation_date, scheduled_departure, scheduled_arrival, status, aircraft_type_id, actual_arrival)
SELECT s.schedule_id, sl.slot_id, CURRENT_DATE,
       CURRENT_DATE + TIME '13:00', CURRENT_DATE + TIME '16:30',
       'ARRIVED', (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'A320' LIMIT 1),
       CURRENT_DATE + TIME '16:28:00'
FROM schedule s
JOIN schedule_slot sl ON sl.schedule_id = s.schedule_id AND sl.day_of_week = 3
WHERE s.flight_number = 'SU-1103';

-- Вылетевший экземпляр SU-1002 (вчера, ближайший слот)
INSERT INTO flight (schedule_id, slot_id, operation_date, scheduled_departure, scheduled_arrival, status, aircraft_type_id, actual_departure)
SELECT s.schedule_id, sl.slot_id, CURRENT_DATE - INTERVAL '1 day',
       (CURRENT_DATE - INTERVAL '1 day') + TIME '08:30',
       (CURRENT_DATE - INTERVAL '1 day') + TIME '12:00',
       'DEPARTED', (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'A320' LIMIT 1),
       (CURRENT_DATE - INTERVAL '1 day') + TIME '08:35:00'
FROM schedule s
JOIN schedule_slot sl ON sl.schedule_id = s.schedule_id AND sl.day_of_week = 6
WHERE s.flight_number = 'SU-1002';

INSERT INTO flight (schedule_id, slot_id, operation_date, scheduled_departure, scheduled_arrival, status, aircraft_type_id)
SELECT s.schedule_id, sl.slot_id, CURRENT_DATE + INTERVAL '1 day',
       (CURRENT_DATE + INTERVAL '1 day') + TIME '17:00',
       (CURRENT_DATE + INTERVAL '1 day') + TIME '18:30',
       'SCHEDULED', (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'B738' LIMIT 1)
FROM schedule s
JOIN schedule_slot sl ON sl.schedule_id = s.schedule_id
WHERE s.flight_number = 'DP-206';

INSERT INTO flight (schedule_id, slot_id, operation_date, scheduled_departure, scheduled_arrival, status, aircraft_type_id)
SELECT s.schedule_id, sl.slot_id, CURRENT_DATE + INTERVAL '2 days',
       (CURRENT_DATE + INTERVAL '2 days') + TIME '14:15',
       (CURRENT_DATE + INTERVAL '2 days') + TIME '15:45',
       'CANCELLED', (SELECT aircraft_type_id FROM aircraft_type WHERE icao_code = 'B738' LIMIT 1)
FROM schedule s
JOIN schedule_slot sl ON sl.schedule_id = s.schedule_id
WHERE s.flight_number = 'DP-205';

-- Назначения гейтов
INSERT INTO gate_assignment (assigned_from, assigned_to, flight_id, gate_id)
SELECT f.scheduled_departure - INTERVAL '45 minutes', f.scheduled_departure + INTERVAL '45 minutes', f.flight_id, g.gate_id
FROM flight f
JOIN schedule s ON s.schedule_id = f.schedule_id AND s.flight_number = 'SU-1002'
JOIN gate g ON g.gate_number = '101'
WHERE f.status = 'SCHEDULED';

INSERT INTO gate_assignment (assigned_from, assigned_to, flight_id, gate_id)
SELECT f.scheduled_departure - INTERVAL '45 minutes', f.scheduled_departure + INTERVAL '75 minutes', f.flight_id, g.gate_id
FROM flight f
JOIN schedule s ON s.schedule_id = f.schedule_id AND s.flight_number = 'DP-205'
JOIN gate g ON g.gate_number = '105'
WHERE f.status = 'DELAYED';

INSERT INTO gate_assignment (assigned_from, assigned_to, flight_id, gate_id)
SELECT f.scheduled_departure - INTERVAL '30 minutes', f.scheduled_departure + INTERVAL '90 minutes', f.flight_id, g.gate_id
FROM flight f
JOIN schedule s ON s.schedule_id = f.schedule_id AND s.flight_number = 'SU-1002'
JOIN gate g ON g.gate_number = '106'
WHERE f.status = 'DEPARTED';

INSERT INTO gate_assignment (assigned_from, assigned_to, flight_id, gate_id)
SELECT f.scheduled_arrival - INTERVAL '45 minutes', f.scheduled_arrival + INTERVAL '45 minutes', f.flight_id, g.gate_id
FROM flight f
JOIN schedule s ON s.schedule_id = f.schedule_id AND s.flight_number = 'SU-1103'
JOIN gate g ON g.gate_number = '126'
WHERE f.status = 'ARRIVED';

INSERT INTO delay_warning (delay_minutes, reason, flight_id, created_at)
SELECT 20, 'Задержка по техническим причинам', f.flight_id, CURRENT_TIMESTAMP
FROM flight f
JOIN schedule s ON s.schedule_id = f.schedule_id AND s.flight_number = 'DP-205'
WHERE f.status = 'DELAYED';
