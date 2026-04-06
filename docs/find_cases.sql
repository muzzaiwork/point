SELECT '1_단순적립' as case_type, p.point_key
FROM point p
WHERE p.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1
                  FROM point_event pe
                  WHERE pe.point_key = p.point_key
                    AND pe.event_type IN ('USE', 'USE_CANCEL', 'EXPIRE', 'AUTO_RESTORED'))
    FETCH FIRST 3 ROWS ONLY;

SELECT '2_사용취소후적립취소' as case_type, p.point_key
FROM point p
WHERE p.status = 'CANCELED'
  AND EXISTS (SELECT 1
              FROM point_event pe
              WHERE pe.point_key = p.point_key AND pe.event_type = 'USE')
  AND EXISTS (SELECT 1
              FROM point_event pe
              WHERE pe.point_key = p.point_key AND pe.event_type = 'USE_CANCEL')
  AND EXISTS (SELECT 1
              FROM point_event pe
              WHERE pe.point_key = p.point_key AND pe.event_type = 'EARN_CANCEL')
    FETCH FIRST 3 ROWS ONLY;

SELECT '3_만료' as case_type, p.point_key
FROM point p
WHERE p.status = 'EXPIRED'
  AND EXISTS (SELECT 1
              FROM point_event pe
              WHERE pe.point_key = p.point_key
                AND pe.event_type = 'EXPIRE') FETCH FIRST 3 ROWS ONLY;

SELECT '4_만료후재지급' as case_type, p.point_key
FROM point p
WHERE EXISTS (SELECT 1
              FROM point_event pe
              WHERE pe.point_key = p.point_key
                AND pe.event_type = 'AUTO_RESTORED') FETCH FIRST 3 ROWS ONLY;
SELECT '5_재지급여러번' as case_type, p.root_point_key, COUNT(*) as cnt
FROM point p
WHERE p.point_type = 'AUTO_RESTORED'
GROUP BY p.root_point_key
HAVING COUNT(*) >= 2 FETCH FIRST 3 ROWS ONLY;
