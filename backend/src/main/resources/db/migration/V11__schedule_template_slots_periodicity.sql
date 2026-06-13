-- Schedule = шаблон + периодичность; schedule_slot = день/время; flight = экземпляр на дату.

CREATE TABLE schedule_slot (
    slot_id         SERIAL PRIMARY KEY,
    schedule_id     INTEGER     NOT NULL REFERENCES schedule(schedule_id) ON DELETE CASCADE,
    day_of_week     SMALLINT    NULL CHECK (day_of_week IS NULL OR (day_of_week >= 1 AND day_of_week <= 7)),
    departure_time  TIME        NOT NULL,
    arrival_time    TIME        NOT NULL
);

ALTER TABLE schedule
    ADD COLUMN effective_from   DATE,
    ADD COLUMN effective_to     DATE,
    ADD COLUMN is_active        BOOLEAN     NOT NULL DEFAULT true,
    ADD COLUMN periodicity_type VARCHAR(20) NOT NULL DEFAULT 'WEEKLY',
    ADD COLUMN periodicity_step INTEGER     NOT NULL DEFAULT 1;

ALTER TABLE flight
    ADD COLUMN slot_id              INTEGER,
    ADD COLUMN operation_date       DATE,
    ADD COLUMN scheduled_departure  TIMESTAMP,
    ADD COLUMN scheduled_arrival    TIMESTAMP;

-- Миграция данных из старых timestamp в schedule
UPDATE schedule s SET
    effective_from = s.scheduled_departure::date,
    periodicity_type = 'WEEKLY',
    periodicity_step = 1,
    is_active = true;

INSERT INTO schedule_slot (schedule_id, day_of_week, departure_time, arrival_time)
SELECT
    s.schedule_id,
    CASE WHEN EXTRACT(DOW FROM s.scheduled_departure)::int = 0 THEN 7
         ELSE EXTRACT(DOW FROM s.scheduled_departure)::int END,
    s.scheduled_departure::time,
    s.scheduled_arrival::time
FROM schedule s;

UPDATE flight f SET
    operation_date = COALESCE(
        f.actual_departure::date,
        f.actual_arrival::date,
        s.scheduled_departure::date
    ),
    scheduled_departure = COALESCE(f.actual_departure, s.scheduled_departure),
    scheduled_arrival = COALESCE(f.actual_arrival, s.scheduled_arrival),
    slot_id = (
        SELECT sl.slot_id FROM schedule_slot sl
        WHERE sl.schedule_id = f.schedule_id
        LIMIT 1
    )
    
FROM schedule s
WHERE f.schedule_id = s.schedule_id;

ALTER TABLE schedule
    ALTER COLUMN effective_from SET NOT NULL;

ALTER TABLE flight
    ALTER COLUMN slot_id SET NOT NULL,
    ALTER COLUMN operation_date SET NOT NULL,
    ALTER COLUMN scheduled_departure SET NOT NULL,
    ALTER COLUMN scheduled_arrival SET NOT NULL;

ALTER TABLE schedule
    ADD CONSTRAINT chk_schedule_periodicity_type
        CHECK (periodicity_type IN ('WEEKLY', 'INTERVAL')),
    ADD CONSTRAINT chk_schedule_periodicity_step
        CHECK (periodicity_step >= 1),
    ADD CONSTRAINT chk_schedule_effective_range
        CHECK (effective_to IS NULL OR effective_to >= effective_from);

ALTER TABLE flight
    ADD CONSTRAINT fk_flight_slot
        FOREIGN KEY (slot_id) REFERENCES schedule_slot(slot_id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX uk_flight_slot_operation_date ON flight (slot_id, operation_date);

CREATE UNIQUE INDEX uk_schedule_slot_weekly_dow
    ON schedule_slot (schedule_id, day_of_week)
    WHERE day_of_week IS NOT NULL;

CREATE INDEX idx_schedule_slot_schedule ON schedule_slot (schedule_id);
CREATE INDEX idx_flight_scheduled_departure ON flight (scheduled_departure);
CREATE INDEX idx_flight_operation_date ON flight (operation_date);

ALTER TABLE schedule
    DROP COLUMN scheduled_departure,
    DROP COLUMN scheduled_arrival;
