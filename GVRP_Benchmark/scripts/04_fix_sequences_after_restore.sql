-- =====================================================================
-- 04_fix_sequences_after_restore.sql
--
-- CHẠY SAU MỖI LẦN RESTORE DATASET. Bỏ qua bước này thì sớm muộn gặp
--   Duplicate entry '1010' for key 'routes.PRIMARY'
--
-- Bối cảnh: Route / RouteStop / UnassignedOrder dùng GenerationType.SEQUENCE
-- với allocationSize = 50. MySQL 8 không có SEQUENCE thật nên Hibernate giả
-- lập bằng bảng route_sequence / route_stop_sequence / unassigned_order_sequence.
-- Sau khi restore, next_val có thể nằm DƯỚI MAX(id) → Hibernate cấp lại ID
-- đã tồn tại.
--
-- ⚠ FILE NÀY CÓ LỆNH UPDATE — hãy đọc hết trước khi chạy.
-- =====================================================================

-- ---------- 1. Chẩn đoán TRƯỚC khi sửa ----------
SELECT 'route_sequence' AS bang, next_val AS next_val_hien_tai,
       (SELECT COALESCE(MAX(id), 0) FROM routes) AS max_id,
       CASE WHEN next_val > (SELECT COALESCE(MAX(id), 0) FROM routes)
            THEN 'ok' ELSE 'PHAI SUA' END AS trang_thai
FROM route_sequence
UNION ALL
SELECT 'route_stop_sequence', next_val,
       (SELECT COALESCE(MAX(id), 0) FROM route_stops),
       CASE WHEN next_val > (SELECT COALESCE(MAX(id), 0) FROM route_stops)
            THEN 'ok' ELSE 'PHAI SUA' END
FROM route_stop_sequence
UNION ALL
SELECT 'unassigned_order_sequence', next_val,
       (SELECT COALESCE(MAX(id), 0) FROM unassigned_orders),
       CASE WHEN next_val > (SELECT COALESCE(MAX(id), 0) FROM unassigned_orders)
            THEN 'ok' ELSE 'PHAI SUA' END
FROM unassigned_order_sequence;


-- ---------- 2. Kéo next_val lên trên MAX(id), dư ra +1000 ----------
-- Tại sao dư +1000: ngữ nghĩa next_val phụ thuộc optimizer pooled/pooled-lo.
-- Vượt dư chỉ tốn ID (vô hại). Vượt thiếu thì vỡ khoá chính.

UPDATE route_sequence
   SET next_val = (SELECT COALESCE(MAX(id), 0) + 1000 FROM routes);

UPDATE route_stop_sequence
   SET next_val = (SELECT COALESCE(MAX(id), 0) + 1000 FROM route_stops);

UPDATE unassigned_order_sequence
   SET next_val = (SELECT COALESCE(MAX(id), 0) + 1000 FROM unassigned_orders);


-- ---------- 3. Xác nhận không cột PK nào bị AUTO_INCREMENT ----------
-- Kết quả mong đợi: 0 dòng.
-- Nếu ra dòng nào: MySQL sẽ tự phát ID sau lưng Hibernate và sớm muộn đụng
-- vào dải mà sequence sắp cấp. Phải ALTER bỏ AUTO_INCREMENT cột đó.
SELECT TABLE_NAME, COLUMN_NAME, EXTRA
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('routes', 'route_stops', 'unassigned_orders')
  AND EXTRA LIKE '%auto_increment%';


-- ---------- 4. Xác nhận lại sau khi sửa ----------
SELECT 'route_sequence' AS bang, next_val,
       (SELECT COALESCE(MAX(id), 0) FROM routes) AS max_id FROM route_sequence
UNION ALL
SELECT 'route_stop_sequence', next_val,
       (SELECT COALESCE(MAX(id), 0) FROM route_stops) FROM route_stop_sequence
UNION ALL
SELECT 'unassigned_order_sequence', next_val,
       (SELECT COALESCE(MAX(id), 0) FROM unassigned_orders) FROM unassigned_order_sequence;


-- =====================================================================
-- KHÔNG đổi các entity này sang GenerationType.IDENTITY để "cho đơn giản".
-- IDENTITY vô hiệu hoá JDBC batch insert (batch_size=50, order_inserts=true)
-- và giết hiệu năng ở job 10k orders.
-- =====================================================================
