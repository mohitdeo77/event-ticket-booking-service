CREATE TABLE events (
    id          UUID PRIMARY KEY,
    name        VARCHAR(200)             NOT NULL,
    venue       VARCHAR(200)             NOT NULL,
    starts_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE seats (
    id              UUID PRIMARY KEY,
    event_id        UUID        NOT NULL REFERENCES events (id),
    row_label       VARCHAR(5)  NOT NULL,
    seat_number     INT         NOT NULL,
    status          VARCHAR(20) NOT NULL,
    reservation_id  UUID,
    version         BIGINT      NOT NULL,
    CONSTRAINT uq_seat_position UNIQUE (event_id, row_label, seat_number)
);

CREATE INDEX idx_seats_event ON seats (event_id);

CREATE TABLE reservations (
    id               UUID PRIMARY KEY,
    event_id         UUID                     NOT NULL REFERENCES events (id),
    user_id          VARCHAR(100)             NOT NULL,
    status           VARCHAR(20)              NOT NULL,
    idempotency_key  VARCHAR(100)             NOT NULL,
    request_hash     VARCHAR(64)              NOT NULL,
    expires_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    version          BIGINT                   NOT NULL,
    CONSTRAINT uq_reservation_idempotency_key UNIQUE (idempotency_key)
);

-- Supports the expiry sweeper: "HELD reservations whose hold has run out"
CREATE INDEX idx_reservations_status_expiry ON reservations (status, expires_at);

CREATE TABLE reservation_seats (
    reservation_id  UUID NOT NULL REFERENCES reservations (id),
    seat_id         UUID NOT NULL REFERENCES seats (id),
    PRIMARY KEY (reservation_id, seat_id)
);
