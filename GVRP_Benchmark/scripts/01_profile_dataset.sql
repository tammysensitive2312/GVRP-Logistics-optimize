-- =====================================================================
-- 01_profile_dataset.sql
-- Thu thập số liệu đặc tả dataset để điền vào DATASET.md.
-- CHỈ ĐỌC — không có ALTER/UPDATE/DELETE nào trong file này.
--
-- Chạy:  mysql -u root -p gvrp_db < 01_profile_dataset.sql > profile_out.txt
-- =====================================================================

-- ---------- 1. Số bản ghi từng bảng ----------
SELECT 'branches'      AS bang, COUNT(*) AS so_ban_ghi FROM branches
UNION ALL SELECT 'depots',        COUNT(*) FROM depots
UNION ALL SELECT 'fleets',        COUNT(*) FROM fleets
UNION ALL SELECT 'vehicle_types', COUNT(*) FROM vehicle_types
UNION ALL SELECT 'vehicles',      COUNT(*) FROM vehicles
UNION ALL SELECT 'orders',        COUNT(*) FROM orders;

-- ---------- 2. Đặc tả orders ----------
SELECT
    COUNT(*)                                        AS tong_order,
    SUM(demand)                                     AS tong_demand,
    MIN(demand)                                     AS demand_min,
    MAX(demand)                                     AS demand_max,
    ROUND(AVG(demand), 2)                           AS demand_tb,
    SUM(time_window_start IS NOT NULL
        AND time_window_end IS NOT NULL)            AS co_time_window,
    ROUND(100.0 * SUM(time_window_start IS NOT NULL
        AND time_window_end IS NOT NULL) / COUNT(*), 1) AS pct_co_tw,
    SUM(service_time IS NULL)                       AS thieu_service_time,
    MIN(delivery_date)                              AS ngay_som_nhat,
    MAX(delivery_date)                              AS ngay_muon_nhat,
    COUNT(DISTINCT delivery_date)                   AS so_ngay_khac_nhau
FROM orders;

-- ---------- 3. Phân bố theo delivery_date ----------
-- Quan trọng: một job lập tuyến thường lấy orders theo MỘT ngày. Con số
-- "6183 orders" chỉ có nghĩa nếu biết nó là của một ngày hay gộp nhiều ngày.
SELECT delivery_date, COUNT(*) AS so_order, SUM(demand) AS demand
FROM orders
GROUP BY delivery_date
ORDER BY so_order DESC
LIMIT 20;

-- ---------- 4. Phân bố status ----------
SELECT status, COUNT(*) AS so_order FROM orders GROUP BY status;

-- ---------- 5. Bounding box toạ độ ----------
-- LƯU Ý ĐƠN VỊ: với SRID 4326, MySQL 8 dùng thứ tự trục lat-long, nên
-- ST_X trả về VĨ ĐỘ và ST_Y trả về KINH ĐỘ. Nếu số ra ngoài khoảng
-- Việt Nam (vĩ độ 8..24, kinh độ 102..110) thì hai trục đang bị đảo —
-- kiểm tra lại cách ghi Point ở tầng ứng dụng trước khi kết luận.
SELECT
    MIN(ST_X(location)) AS truc1_min, MAX(ST_X(location)) AS truc1_max,
    MIN(ST_Y(location)) AS truc2_min, MAX(ST_Y(location)) AS truc2_max
FROM orders;

SELECT id, name, ST_X(location) AS truc1, ST_Y(location) AS truc2 FROM depots;

-- ---------- 6. Đội xe theo loại ----------
SELECT
    vt.id, vt.type_name, vt.capacity, vt.fixed_cost,
    vt.cost_per_km, vt.cost_per_hour, vt.max_distance, vt.max_duration,
    COUNT(v.id) AS so_xe
FROM vehicle_types vt
LEFT JOIN vehicles v ON v.vehicle_type_id = vt.id
GROUP BY vt.id, vt.type_name, vt.capacity, vt.fixed_cost,
         vt.cost_per_km, vt.cost_per_hour, vt.max_distance, vt.max_duration
ORDER BY vt.id;

-- ---------- 7. Tổng capacity đội xe vs tổng demand ----------
-- CLAUDE.md ghi tổng demand chỉ bằng ~10,5% sức chứa đội xe. Con số này
-- xác nhận lại, và là căn cứ để nói "load utilization thấp là ĐÚNG,
-- không phải bug" — ràng buộc thật là thời gian, không phải tải.
SELECT
    (SELECT SUM(vt.capacity)
       FROM vehicles v JOIN vehicle_types vt ON vt.id = v.vehicle_type_id) AS tong_capacity,
    (SELECT SUM(demand) FROM orders)                                       AS tong_demand;

-- ---------- 8. GUARD: max_duration phải là GIỜ ----------
-- Bẫy đã trả giá: DB từng lưu phút (480/600) trong khi engine nhân 3600
-- như giờ, làm ràng buộc ca làm bị vô hiệu hoàn toàn.
SELECT id, type_name, max_duration,
       CASE WHEN max_duration > 24
            THEN 'SAI — engine sẽ ném exception; giá trị đang là PHÚT?'
            ELSE 'ok (đơn vị giờ)' END AS chan_doan
FROM vehicle_types;

-- ---------- 9. GUARD: tên loại xe mâu thuẫn capacity ----------
-- CLAUDE.md ghi: 'Truck 10T Large' capacity 500 kg, 'Truck 5T Small'
-- capacity 100 kg — tên và giá trị không khớp nhau.
SELECT id, type_name, capacity FROM vehicle_types ORDER BY capacity;

-- ---------- 10. GUARD: secret trong bảng branches ----------
-- branch_webhook_url là Slack incoming webhook — ĐÂY LÀ SECRET.
-- Nếu có giá trị, KHÔNG được để nó vào file dataset sẽ commit.
SELECT id, name,
       CASE WHEN branch_webhook_url IS NULL OR branch_webhook_url = ''
            THEN 'rỗng — an toàn'
            ELSE 'CÓ GIÁ TRỊ — phải scrub trước khi commit' END AS webhook
FROM branches;

-- ---------- 11. GUARD: sequence vs MAX(id) ----------
-- SEQUENCE và AUTO_INCREMENT không được sống chung. next_val phải NẰM TRÊN
-- MAX(id), nếu không sẽ đụng dải ID mà Hibernate sắp cấp.
SELECT 'route_sequence' AS bang, next_val,
       (SELECT COALESCE(MAX(id), 0) FROM routes) AS max_id_hien_tai FROM route_sequence
UNION ALL
SELECT 'route_stop_sequence', next_val,
       (SELECT COALESCE(MAX(id), 0) FROM route_stops) FROM route_stop_sequence
UNION ALL
SELECT 'unassigned_order_sequence', next_val,
       (SELECT COALESCE(MAX(id), 0) FROM unassigned_orders) FROM unassigned_order_sequence;

-- ---------- 12. GUARD: cột PK không được AUTO_INCREMENT ----------
-- Kết quả mong đợi: 0 dòng.
SELECT TABLE_NAME, COLUMN_NAME, EXTRA
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('routes', 'route_stops', 'unassigned_orders')
  AND EXTRA LIKE '%auto_increment%';

-- ---------- 13. GUARD: độ rộng cột tiền tệ ----------
-- solutions.total_cost phải là DECIMAL(18,2). Bản (10,2) cũ chỉ chứa được
-- 99.999.999,99 và đã tràn ở job 6183 orders (cost thật 264.655.468 VND).
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND COLUMN_NAME IN ('total_cost', 'total_distance', 'total_co2', 'total_time');
