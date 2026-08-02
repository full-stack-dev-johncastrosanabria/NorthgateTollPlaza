-- Demo seed data matching the UI prototype. PIN for both accounts: 1234 (BCrypt).

INSERT INTO staff (staff_code, full_name, role, pin_hash) VALUES
    ('OP-14', 'R. Alvarez', 'OPERATOR', '$2y$10$cBu0Ck2bJbKDkiGnID52/uFljDAT0w1xgKF/RWhSKH.cCViq39wfC'),
    ('MG-02', 'D. Okafor',  'MANAGER',  '$2y$10$cBu0Ck2bJbKDkiGnID52/uFljDAT0w1xgKF/RWhSKH.cCViq39wfC'),
    ('OP-07', 'M. Iqbal',   'OPERATOR', '$2y$10$cBu0Ck2bJbKDkiGnID52/uFljDAT0w1xgKF/RWhSKH.cCViq39wfC'),
    ('OP-09', 'S. Navarro', 'OPERATOR', '$2y$10$cBu0Ck2bJbKDkiGnID52/uFljDAT0w1xgKF/RWhSKH.cCViq39wfC'),
    ('OP-11', 'T. Brandt',  'OPERATOR', '$2y$10$cBu0Ck2bJbKDkiGnID52/uFljDAT0w1xgKF/RWhSKH.cCViq39wfC');

INSERT INTO lane (lane_number, mode, status, queue_length) VALUES
    (1, 'MANNED',    'OPEN',   2),
    (2, 'MANNED',    'OPEN',   5),
    (3, 'MANNED',    'OPEN',   3),
    (4, 'MANNED',    'FAULT',  7),
    (5, 'AUTOMATED', 'OPEN',   1),
    (6, 'AUTOMATED', 'CLOSED', 0);

INSERT INTO vehicle_class (code, label, fare, sort_order) VALUES
    ('MOTORCYCLE', 'Motorcycle', 1.50,  1),
    ('CAR',        'Car',        3.00,  2),
    ('VAN_SUV',    'Van / SUV',  4.50,  3),
    ('BUS',        'Bus',        9.00,  4),
    ('TRUCK',      'Truck',      14.00, 5);

-- Today's 06:00–14:00 shifts on the manned lanes (lane 3 = OP-14, as in the prototype)
INSERT INTO shift (staff_id, lane_id, starts_at, ends_at, status)
SELECT s.id, l.id, date_trunc('day', now()) + interval '6 hours', date_trunc('day', now()) + interval '14 hours', 'ACTIVE'
FROM (VALUES ('OP-07', 1), ('OP-09', 2), ('OP-14', 3), ('OP-11', 4)) AS a(staff_code, lane_number)
JOIN staff s ON s.staff_code = a.staff_code
JOIN lane  l ON l.lane_number = a.lane_number;

-- Recent passes on lane 3, as shown in the prototype's "Recent passes" list
INSERT INTO pass (lane_id, shift_id, vehicle_class_id, plate, payment_method, amount, created_at)
SELECT l.id, sh.id, vc.id, p.plate, p.method, vc.fare, date_trunc('day', now()) + p.at::interval
FROM (VALUES
    ('PQE 0032', 'CAR',     'TAG',  '9 hours 38 minutes'),
    ('BNS 4417', 'VAN_SUV', 'CASH', '9 hours 39 minutes'),
    ('LMD 2210', 'TRUCK',   'CARD', '9 hours 41 minutes'),
    ('KTR 8891', 'CAR',     'TAG',  '9 hours 42 minutes')
) AS p(plate, class_code, method, at)
JOIN vehicle_class vc ON vc.code = p.class_code
JOIN lane l  ON l.lane_number = 3
JOIN shift sh ON sh.lane_id = l.id AND sh.status = 'ACTIVE';

-- Lane 3 exceptions from the prototype (two open, one cleared)
INSERT INTO lane_exception (lane_id, shift_id, plate, type, description, status, created_at, resolved_at, resolved_by)
SELECT l.id, sh.id, e.plate, e.type, e.description, e.status,
       date_trunc('day', now()) + e.at::interval,
       CASE WHEN e.status = 'CLEARED' THEN date_trunc('day', now()) + e.at::interval + interval '2 minutes' END,
       CASE WHEN e.status = 'CLEARED' THEN (SELECT id FROM staff WHERE staff_code = 'OP-14') END
FROM (VALUES
    ('GLA 5583', 'OVERPAYMENT', 'Cash overpayment, change issued',   'CLEARED', '9 hours 12 minutes'),
    ('TSD 1190', 'VIOLATION',   'Passed gate without payment',       'OPEN',    '9 hours 31 minutes'),
    ('HRV 7745', 'UNREAD_TAG',  'Tag not detected — vehicle waiting','OPEN',    '9 hours 40 minutes')
) AS e(plate, type, description, status, at)
JOIN lane l ON l.lane_number = 3
JOIN shift sh ON sh.lane_id = l.id AND sh.status = 'ACTIVE';
