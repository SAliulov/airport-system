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
-- Учётные записи (JWT)
-- =============================================
CREATE TABLE app_user (
    user_id       SERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(32)  NOT NULL
        CHECK (role IN ('DISPATCHER', 'READ_ONLY'))
);

-- =============================================
-- Шаблон расписания
-- =============================================
CREATE TABLE schedule (
    schedule_id         SERIAL PRIMARY KEY,
    flight_number       VARCHAR(20) NOT NULL,
    origin_airport      CHAR(3)     NOT NULL,
    destination_airport CHAR(3)     NOT NULL,
    effective_from      DATE        NOT NULL,
    effective_to        DATE,
    is_active           BOOLEAN     NOT NULL DEFAULT true,
    periodicity_type    VARCHAR(20) NOT NULL DEFAULT 'WEEKLY'
        CHECK (periodicity_type IN ('WEEKLY', 'INTERVAL')),
    periodicity_step    INTEGER     NOT NULL DEFAULT 1
        CHECK (periodicity_step >= 1),
    airline_id          INTEGER     NOT NULL
        REFERENCES airline(airline_id) ON DELETE RESTRICT,
    CONSTRAINT chk_schedule_effective_range
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

-- =============================================
-- Слот шаблона (день недели / время)
-- =============================================
CREATE TABLE schedule_slot (
    slot_id        SERIAL PRIMARY KEY,
    schedule_id    INTEGER  NOT NULL
        REFERENCES schedule(schedule_id) ON DELETE CASCADE,
    day_of_week    SMALLINT CHECK (day_of_week IS NULL OR (day_of_week >= 1 AND day_of_week <= 7)),
    departure_time TIME     NOT NULL,
    arrival_time   TIME     NOT NULL
);

-- =============================================
-- Конкретный выполняемый рейс (экземпляр)
-- =============================================
CREATE TABLE flight (
    flight_id          SERIAL PRIMARY KEY,
    schedule_id        INTEGER     NOT NULL
        REFERENCES schedule(schedule_id) ON DELETE RESTRICT,
    slot_id            INTEGER     NOT NULL
        REFERENCES schedule_slot(slot_id) ON DELETE RESTRICT,
    operation_date     DATE        NOT NULL,
    scheduled_departure TIMESTAMP  NOT NULL,
    scheduled_arrival   TIMESTAMP  NOT NULL,
    actual_departure   TIMESTAMP,
    actual_arrival     TIMESTAMP,
    status             VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED','DEPARTED','ARRIVED','DELAYED','CANCELLED')),
    aircraft_type_id   INTEGER
        REFERENCES aircraft_type(aircraft_type_id) ON DELETE SET NULL,
    CONSTRAINT uk_flight_slot_operation_date UNIQUE (slot_id, operation_date)
);

-- =============================================
-- Назначение гейта на рейс (с интервалом)
-- =============================================
CREATE TABLE gate_assignment (
    assignment_id SERIAL PRIMARY KEY,
    flight_id     INTEGER   NOT NULL
        REFERENCES flight(flight_id) ON DELETE CASCADE,
    gate_id       INTEGER   NOT NULL
        REFERENCES gate(gate_id) ON DELETE RESTRICT,
    assigned_from TIMESTAMP NOT NULL,
    assigned_to   TIMESTAMP NOT NULL
);

-- =============================================
-- Предупреждения о задержке рейса
-- =============================================
CREATE TABLE delay_warning (
    warning_id    SERIAL PRIMARY KEY,
    flight_id     INTEGER      NOT NULL
        REFERENCES flight(flight_id) ON DELETE CASCADE,
    delay_minutes INTEGER      NOT NULL,
    reason        VARCHAR(500),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- =============================================
-- Индексы для производительности
-- =============================================
CREATE UNIQUE INDEX uk_schedule_slot_weekly_dow
    ON schedule_slot (schedule_id, day_of_week)
    WHERE day_of_week IS NOT NULL;

CREATE INDEX idx_schedule_slot_schedule ON schedule_slot (schedule_id);
CREATE INDEX idx_flight_status ON flight (status);
CREATE INDEX idx_flight_schedule ON flight (schedule_id);
CREATE INDEX idx_flight_scheduled_departure ON flight (scheduled_departure);
CREATE INDEX idx_flight_operation_date ON flight (operation_date);
CREATE INDEX idx_gate_assign_gate ON gate_assignment (gate_id);
CREATE INDEX idx_gate_assign_from ON gate_assignment (assigned_from);
CREATE INDEX idx_gate_assign_to ON gate_assignment (assigned_to);
CREATE INDEX idx_delay_flight ON delay_warning (flight_id);