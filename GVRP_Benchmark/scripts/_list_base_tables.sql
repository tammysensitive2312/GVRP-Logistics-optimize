-- Liệt kê BASE TABLE của database đang kết nối, mỗi dòng một tên.
-- Dùng DATABASE() nên không cần nội suy tên DB từ script gọi.
--
-- Tách ra file riêng có lý do: truyền SQL qua `mysql -e "..."` bên trong
-- `for /f (`...`)` của cmd.exe gây tái phân tách chuỗi ở dấu `=`, khiến
-- docker nhận sai tham số container. Đọc từ file thì không có nháy lồng.
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_TYPE = 'BASE TABLE'
ORDER BY TABLE_NAME;
