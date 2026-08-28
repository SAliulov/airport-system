CREATE TABLE operational_event (
    operational_event_id BIGSERIAL PRIMARY KEY,
    event_timestamp       TIMESTAMP    NOT NULL,
    username               VARCHAR(64)  NOT NULL,
    category               VARCHAR(20)  NOT NULL
        CHECK (category IN ('GATE', 'STATUS', 'AIRCRAFT', 'DELAY', 'SCHEDULE', 'FLIGHT')),
    message                VARCHAR(500) NOT NULL,
    details                VARCHAR(500),
    flight_id              INTEGER,
    flight_number          VARCHAR(20)
);

CREATE INDEX idx_operational_event_timestamp ON operational_event (event_timestamp DESC);
