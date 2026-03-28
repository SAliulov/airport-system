-- =============================================
-- Справочник авиакомпаний
-- =============================================
CREATE TABLE airline (
    airline_id SERIAL PRIMARY KEY,
    iata_code  CHAR(2)      NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    country    VARCHAR(77)
);

-- =============================================
-- Справочник типов воздушных судов
-- =============================================
CREATE TABLE aircraft_type (
    aircraft_type_id   SERIAL PRIMARY KEY,
    icao_code          VARCHAR(4)  NOT NULL UNIQUE,
    passenger_capacity INTEGER,
    size_category      VARCHAR(10) NOT NULL
       CHECK (size_category IN ('NARROW', 'WIDE', 'JUMBO'))
);

-- =============================================
-- Справочник гейтов
-- =============================================
CREATE TABLE gate (
    gate_id           SERIAL PRIMARY KEY,
    gate_number       VARCHAR(10) NOT NULL UNIQUE,
    terminal          VARCHAR(10),
    is_active         BOOLEAN     NOT NULL DEFAULT true,
    max_size_category VARCHAR(10) NOT NULL
      CHECK (max_size_category IN ('NARROW', 'WIDE', 'JUMBO'))
);

-- =============================================
-- Плановое расписание рейсов
-- =============================================
CREATE TABLE schedule (
    schedule_id         SERIAL PRIMARY KEY,
    flight_number       VARCHAR(20) NOT NULL,
    origin_airport      CHAR(3)     NOT NULL,
    destination_airport CHAR(3)     NOT NULL,
    scheduled_departure TIMESTAMP   NOT NULL,
    scheduled_arrival   TIMESTAMP   NOT NULL,
    airline_id          INTEGER     NOT NULL
      REFERENCES airline(airline_id) ON DELETE RESTRICT
);

-- =============================================
-- Конкретный выполняемый рейс
-- =============================================
CREATE TABLE flight (
    flight_id        SERIAL PRIMARY KEY,
    actual_departure TIMESTAMP,
    actual_arrival   TIMESTAMP,
    status           VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED','DEPARTED','ARRIVED','DELAYED','CANCELLED')),
    schedule_id      INTEGER     NOT NULL
        REFERENCES schedule(schedule_id) ON DELETE RESTRICT,
    aircraft_type_id INTEGER REFERENCES aircraft_type(aircraft_type_id) ON DELETE SET NULL
);

-- =============================================
-- Назначение гейта на рейс (с интервалом)
-- =============================================
CREATE TABLE gate_assignment (
    assignment_id SERIAL PRIMARY KEY,
    assigned_from TIMESTAMP NOT NULL,
    assigned_to   TIMESTAMP NOT NULL,
    flight_id     INTEGER   NOT NULL
     REFERENCES flight(flight_id) ON DELETE CASCADE,
    gate_id       INTEGER   NOT NULL
     REFERENCES gate(gate_id) ON DELETE RESTRICT
);

-- =============================================
-- Предупреждения о задержке рейса
-- =============================================
CREATE TABLE delay_warning (
    warning_id    SERIAL PRIMARY KEY,
    delay_minutes INTEGER      NOT NULL,
    reason        VARCHAR(500),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    flight_id     INTEGER      NOT NULL
       REFERENCES flight(flight_id) ON DELETE CASCADE
);

-- =============================================
-- Индексы для производительности
-- =============================================
CREATE INDEX idx_flight_status      ON flight(status);
CREATE INDEX idx_flight_schedule    ON flight(schedule_id);
CREATE INDEX idx_gate_assign_gate   ON gate_assignment(gate_id);
CREATE INDEX idx_gate_assign_from   ON gate_assignment(assigned_from);
CREATE INDEX idx_gate_assign_to     ON gate_assignment(assigned_to);
CREATE INDEX idx_delay_flight       ON delay_warning(flight_id);

-- =============================================
-- Тестовые данные (справочники)
-- =============================================
INSERT INTO airline (iata_code, name, country) VALUES
    ('SU', 'Аэрофлот', 'Россия'),
    ('S7', 'S7 Airlines', 'Россия'),
    ('U6', 'Уральские авиалинии', 'Россия');

INSERT INTO aircraft_type (icao_code, passenger_capacity, size_category) VALUES
    ('A320', 180, 'NARROW'),
    ('B738', 189, 'NARROW'),
    ('B77W', 396, 'WIDE'),
    ('A388', 555, 'JUMBO');

INSERT INTO gate (gate_number, terminal, is_active, max_size_category) VALUES
    ('A1', 'A', true, 'NARROW'),
    ('A2', 'A', true, 'WIDE'),
    ('B1', 'B', true, 'NARROW'),
    ('B2', 'B', true, 'JUMBO'),
    ('C1', 'C', false, 'NARROW');