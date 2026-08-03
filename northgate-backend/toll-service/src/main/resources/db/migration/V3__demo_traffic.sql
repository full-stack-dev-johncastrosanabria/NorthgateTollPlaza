-- Demo traffic so the manager dashboard has a plaza-scale day to aggregate.
-- Lane 3 is deliberately excluded: V2 gives it the handful of passes the
-- lane-console prototype shows, and this would drown them.

INSERT INTO pass (lane_id, shift_id, vehicle_class_id, plate, payment_method, amount, created_at)
SELECT
    l.id,
    sh.id,
    vc.id,
    'DMO ' || lpad(((hrs.h * 1000 + g.n) % 10000)::text, 4, '0'),
    (ARRAY['CASH', 'CARD', 'TAG', 'TAG'])[1 + (g.n % 4)],
    vc.fare,
    date_trunc('day', now())
        + make_interval(hours => hrs.h, mins => (g.n * 7) % 60, secs => (g.n * 13) % 60)
FROM (VALUES (5, 88), (6, 176), (7, 314), (8, 402), (9, 356), (10, 231)) AS hrs(h, cnt)
CROSS JOIN LATERAL generate_series(1, hrs.cnt) AS g(n)
JOIN lane l
    ON l.lane_number = (ARRAY[1, 2, 4, 5])[1 + (g.n % 4)]
LEFT JOIN shift sh
    ON sh.lane_id = l.id AND sh.status = 'ACTIVE'
JOIN vehicle_class vc
    ON vc.code = (ARRAY['CAR','CAR','CAR','VAN_SUV','CAR','MOTORCYCLE','TRUCK','CAR','VAN_SUV','BUS'])[1 + (g.n % 10)]
WHERE hrs.h <= EXTRACT(HOUR FROM now());

-- Exceptions awaiting manager review on the other lanes (lane 3 already has its own).
INSERT INTO lane_exception (lane_id, shift_id, plate, type, description, status, created_at)
SELECT l.id, sh.id, e.plate, e.type, e.description, 'OPEN',
       date_trunc('day', now()) + e.at::interval
FROM (VALUES
    (1, 'RKP 2251', 'UNREAD_TAG',  'Tag not detected — vehicle waiting', '8 hours 55 minutes'),
    (4, 'WQN 8830', 'VIOLATION',   'Passed gate without payment',        '7 hours 20 minutes'),
    (4, 'JHT 4419', 'UNREAD_TAG',  'Tag not detected — vehicle waiting', '8 hours 05 minutes'),
    (4, 'BVC 7702', 'VIOLATION',   'Passed gate without payment',        '9 hours 15 minutes'),
    (4, 'MSD 1174', 'OVERPAYMENT', 'Cash overpayment, change pending',   '9 hours 48 minutes')
) AS e(lane_number, plate, type, description, at)
JOIN lane l ON l.lane_number = e.lane_number
LEFT JOIN shift sh ON sh.lane_id = l.id AND sh.status = 'ACTIVE';
