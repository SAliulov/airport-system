-- Справочники: авиакомпании, типы ВС, гейты

INSERT INTO airline (iata_code, name, country) VALUES
    ('SU', 'Аэрофлот', 'Россия'),
    ('S7', 'S7 Airlines', 'Россия'),
    ('U6', 'Уральские авиалинии', 'Россия'),
    ('DP', 'Победа', 'Россия');

INSERT INTO aircraft_type (icao_code, passenger_capacity, size_category) VALUES
    ('A320', 180, 'NARROW'),
    ('B738', 189, 'NARROW'),
    ('B77W', 396, 'WIDE'),
    ('A388', 555, 'JUMBO');

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
    ('111', 'B', true, 'JUMBO'),
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