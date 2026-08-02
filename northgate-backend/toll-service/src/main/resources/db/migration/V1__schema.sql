-- Northgate Toll Plaza — core schema (see ARCHITECTURE.md §4)

CREATE TABLE staff (
    id            BIGSERIAL PRIMARY KEY,
    staff_code    VARCHAR(10)  NOT NULL UNIQUE,
    full_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('OPERATOR','MANAGER')),
    pin_hash      VARCHAR(100) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE lane (
    id            BIGSERIAL PRIMARY KEY,
    lane_number   INT          NOT NULL UNIQUE,
    mode          VARCHAR(20)  NOT NULL CHECK (mode IN ('MANNED','AUTOMATED')),
    status        VARCHAR(20)  NOT NULL CHECK (status IN ('OPEN','CLOSED','FAULT')),
    queue_length  INT          NOT NULL DEFAULT 0
);

CREATE TABLE vehicle_class (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(20)  NOT NULL UNIQUE,
    label         VARCHAR(40)  NOT NULL,
    fare          NUMERIC(8,2) NOT NULL CHECK (fare >= 0),
    sort_order    INT          NOT NULL
);

CREATE TABLE shift (
    id            BIGSERIAL PRIMARY KEY,
    staff_id      BIGINT       NOT NULL REFERENCES staff(id),
    lane_id       BIGINT       NOT NULL REFERENCES lane(id),
    starts_at     TIMESTAMPTZ  NOT NULL,
    ends_at       TIMESTAMPTZ  NOT NULL,
    status        VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE','CLOSED'))
);

CREATE UNIQUE INDEX uq_shift_active_lane ON shift(lane_id) WHERE status = 'ACTIVE';

CREATE TABLE pass (
    id               BIGSERIAL PRIMARY KEY,
    lane_id          BIGINT       NOT NULL REFERENCES lane(id),
    shift_id         BIGINT       REFERENCES shift(id),
    vehicle_class_id BIGINT       NOT NULL REFERENCES vehicle_class(id),
    plate            VARCHAR(15)  NOT NULL,
    payment_method   VARCHAR(10)  NOT NULL CHECK (payment_method IN ('CASH','CARD','TAG')),
    amount           NUMERIC(8,2) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_pass_lane_created ON pass(lane_id, created_at DESC);
CREATE INDEX idx_pass_created      ON pass(created_at);

CREATE TABLE lane_exception (
    id            BIGSERIAL PRIMARY KEY,
    lane_id       BIGINT       NOT NULL REFERENCES lane(id),
    shift_id      BIGINT       REFERENCES shift(id),
    plate         VARCHAR(15),
    type          VARCHAR(20)  NOT NULL CHECK (type IN ('UNREAD_TAG','VIOLATION','OVERPAYMENT')),
    description   VARCHAR(200) NOT NULL,
    status        VARCHAR(20)  NOT NULL CHECK (status IN ('OPEN','CLEARED','OVERRIDDEN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at   TIMESTAMPTZ,
    resolved_by   BIGINT       REFERENCES staff(id)
);

CREATE INDEX idx_exception_open ON lane_exception(lane_id) WHERE status = 'OPEN';
