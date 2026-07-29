-- =====================================================================
-- 05_instance_candidates.sql
-- Tìm branch/ngày nào đủ dữ liệu để dựng instance ở các mốc
-- n = 1000 / 3000 / 6183 / 10000.
-- CHỈ ĐỌC.
--
-- Chạy:
--   docker exec -i -e MYSQL_PWD=<mk> <container> mysql -u root gvrp_db \
--     < 05_instance_candidates.sql > instance_candidates.txt
-- =====================================================================

-- ---------- 1. Mỗi branch có bao nhiêu order KHẢ DỤNG ----------
-- Chỉ tính status = 'SCHEDULED'. Các order COMPLETED / ON_ROUTE đã được
-- lập tuyến rồi, đưa vào instance là sai ngữ nghĩa.
SELECT
    b.id AS branch_id,
    b.name,
    COUNT(o.id)                                        AS order_scheduled,
    COUNT(DISTINCT o.delivery_date)                    AS so_ngay,
    ROUND(COUNT(o.id) / NULLIF(COUNT(DISTINCT o.delivery_date), 0), 0) AS order_moi_ngay_tb,
    SUM(o.demand)                                      AS tong_demand
FROM branches b
LEFT JOIN orders o ON o.branch_id = b.id AND o.status = 'SCHEDULED'
GROUP BY b.id, b.name
ORDER BY order_scheduled DESC;


-- ---------- 2. Mỗi branch có bao nhiêu xe ----------
SELECT
    b.id AS branch_id,
    b.name,
    COUNT(v.id)                        AS so_xe,
    COUNT(DISTINCT vt.id)              AS so_loai_xe,
    SUM(vt.capacity)                   AS tong_capacity,
    COUNT(DISTINCT v.start_depot_id)   AS so_depot_xuat_phat
FROM branches b
LEFT JOIN fleets f       ON f.branch_id = b.id
LEFT JOIN vehicles v     ON v.fleet_id = f.id
LEFT JOIN vehicle_types vt ON vt.id = v.vehicle_type_id
GROUP BY b.id, b.name
ORDER BY so_xe DESC;


-- ---------- 3. CÂU HỎI QUYẾT ĐỊNH: n tối đa của MỘT ngày ----------
-- Nếu instance chỉ gồm một delivery_date (hợp lý về nghiệp vụ) thì n bị
-- chặn bởi con số này. Nếu mốc 6183 / 10000 vượt quá, instance BUỘC phải
-- gộp nhiều ngày — và khi đó phải nói rõ đó là bài toán stress test tổng
-- hợp, không phải tình huống vận hành thật.
SELECT
    o.branch_id,
    b.name,
    o.delivery_date,
    COUNT(*)      AS n_order,
    SUM(o.demand) AS demand
FROM orders o
JOIN branches b ON b.id = o.branch_id
WHERE o.status = 'SCHEDULED'
GROUP BY o.branch_id, b.name, o.delivery_date
ORDER BY n_order DESC
LIMIT 25;


-- ---------- 4. Với branch lớn nhất: bao nhiêu ngày liên tiếp thì đủ n ----------
-- Chạy sau khi biết branch_id lớn nhất từ truy vấn 1. Thay :BID bằng số đó.
-- (MySQL CLI không có biến bind — sửa tay rồi chạy lại phần này.)
--
-- SELECT delivery_date, COUNT(*) AS n,
--        SUM(COUNT(*)) OVER (ORDER BY delivery_date) AS n_luy_tich
-- FROM orders
-- WHERE branch_id = :BID AND status = 'SCHEDULED'
-- GROUP BY delivery_date
-- ORDER BY delivery_date
-- LIMIT 30;


-- ---------- 5. Phân bố time window trong ngày ----------
-- Time window là LocalTime (giờ trong ngày), tách biệt với delivery_date.
-- Nếu instance gộp nhiều ngày, mọi time window bị coi như CÙNG một ngày.
SELECT
    HOUR(time_window_start) AS gio_bat_dau,
    COUNT(*)                AS so_order
FROM orders
WHERE status = 'SCHEDULED' AND time_window_start IS NOT NULL
GROUP BY HOUR(time_window_start)
ORDER BY gio_bat_dau;

SELECT
    MIN(time_window_start) AS tw_som_nhat,
    MAX(time_window_end)   AS tw_muon_nhat,
    ROUND(AVG(TIMESTAMPDIFF(MINUTE,
        CAST(CONCAT('2000-01-01 ', time_window_start) AS DATETIME),
        CAST(CONCAT('2000-01-01 ', time_window_end)   AS DATETIME))), 1) AS do_rong_tw_phut_tb
FROM orders
WHERE status = 'SCHEDULED'
  AND time_window_start IS NOT NULL AND time_window_end IS NOT NULL;


-- ---------- 6. Job đã chạy: lấy lại đúng tập ID để tái lập ----------
-- input_data lưu nguyên request JSON. Đây là cách tái lập CHÍNH XÁC một
-- job đã đo, thay vì lấy mẫu mới.
SELECT
    id, branch_id, status, created_at, started_at, completed_at,
    JSON_LENGTH(input_data -> '$.orderIds')   AS n_orders,
    JSON_LENGTH(input_data -> '$.vehicleIds') AS n_vehicles,
    JSON_LENGTH(input_data -> '$.order_ids')   AS n_orders_snake,
    JSON_LENGTH(input_data -> '$.vehicle_ids') AS n_vehicles_snake
FROM optimization_jobs
ORDER BY id DESC
LIMIT 30;

-- Xem cấu trúc thật của input_data một job (nếu hai cột snake/camel ở trên
-- đều NULL thì khoá lồng khác — dòng này cho biết khoá cấp 1 là gì).
SELECT id, JSON_KEYS(input_data) AS khoa_cap_1
FROM optimization_jobs
WHERE input_data IS NOT NULL
ORDER BY id DESC
LIMIT 5;
