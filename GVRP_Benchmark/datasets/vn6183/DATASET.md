# Corpus `vn_hanoi` — dữ liệu thật của dự án

> **Tên thư mục `vn6183` là di sản lịch sử và gây nhầm.** Corpus có **990.265** đơn, không
> phải 6183 — con số 6183 là quy mô của *một instance* (job #21). Đổi tên khi thuận tiện:
> `move ..\vn6183 ..\corpus_vn_hanoi` rồi sửa đường dẫn trong `.gitignore` và các script.

Số liệu dưới đây lấy từ `scripts/01_profile_dataset.sql`, chạy **2026-07-27**.

## Định danh

| | |
|---|---|
| `dataset_id` | `vn_hanoi_corpus` |
| Nguồn | MySQL `gvrp_db` trong container Docker trên máy phát triển |
| Ngày export | 2026-07-27 |
| Commit lúc export | `1517108` (`git rev-parse --short HEAD`) |
| File | `schema.sql` (23 KB), `data.sql` (**241 MB**), `branches_scrubbed.sql` (1,1 KB) |
| SHA256 | xem các file `.sha256` cùng thư mục |

**`data.sql` bị gitignore có chủ ý.** 241 MB với một dòng đơn dài 1,04 MB — commit vào git
thường là repo phình vĩnh viễn. Corpus là nguồn chân lý cục bộ; thứ được commit là **slice
tự chứa của từng instance** (xem `../instances/`).

## Quy mô

| Bảng | Số bản ghi |
|---|---|
| branches | 13 |
| depots | 28 |
| fleets | 12 |
| vehicle_types | 16 |
| vehicles | **100.192** |
| orders | **990.265** |

## Đặc tả orders

| Chỉ số | Giá trị |
|---|---|
| Tổng demand | 101.484.721,13 |
| Demand min / max / trung bình | 1,50 / 499,70 / 102,48 |
| Có time window | 791.842 (**80,0%**) |
| Thiếu `service_time` | **0** |
| Khoảng `delivery_date` | 2024-01-01 → 2026-01-30 |
| Số ngày khác nhau | **372** |
| Ngày đông nhất | 2024-02-26 — 2.842 đơn |

Con số 80% khớp với ghi chú "20% orders không có time window" trong CLAUDE.md.

### Phân bố status

| Status | Số đơn |
|---|---|
| SCHEDULED | 710.107 |
| COMPLETED | 186.835 |
| ON_ROUTE | 93.238 |
| UNASSIGNED | 85 |

> **Chỉ `SCHEDULED` được dùng để dựng instance.** `COMPLETED` và `ON_ROUTE` đã qua lập
> tuyến; đưa vào bài toán mới là sai ngữ nghĩa và có thể vướng
> `OrderStatusTransitionService`.

## Không gian địa lý

| | |
|---|---|
| Vĩ độ (`ST_X`) | 20,946201 → 21,688596 |
| Kinh độ (`ST_Y`) | 104,873032 → 105,895218 |
| Số depot | 28 |

Xác nhận: với SRID 4326, MySQL 8 trả `ST_X` = **vĩ độ**. Khoảng giá trị nằm gọn trong vùng
Hà Nội và phụ cận, nên hai trục **không** bị đảo.

## Đội xe

| id | type_name | capacity | fixed_cost | cost_per_km | cost_per_hour | max_distance | max_duration | Số xe |
|---|---|---|---|---|---|---|---|---|
| 1 | truck | 5000 | 50.000 | 5.000 | 4.000 | 300 | 8 | 3 |
| 3 | Xe tải 5 tấn | 5000 | 5.000 | 5.000 | 4.000 | 300 | 8 | 1 |
| 100 | Truck 5T Small | **100** | 50.000 | 5.000 | 4.000 | 300 | 8 | 3 |
| 300 | Truck 10T Large | **500** | 50.000 | 5.000 | 4.000 | 500 | 10 | 25 |
| 1001 | Van 1T | 1000 | 30.000 | 3.000 | 2.000 | 200 | 8 | 30 |
| 1002 | Truck 5T | 5000 | 100.000 | 8.000 | 5.000 | 500 | 10 | **99.970** |
| 1011 | Van 1T | 1000 | 30.000 | 3.000 | 2.000 | 200 | 8 | 15 |
| 1012 | Truck 5T | 5000 | 100.000 | 8.000 | 5.000 | 500 | 10 | 5 |
| 1021 | Van 1T | 1000 | 30.000 | 3.000 | 2.000 | 200 | 8 | 15 |
| 1022 | Truck 5T | 5000 | 100.000 | 8.000 | 5.000 | 500 | 10 | 5 |
| 1031 | Van 1T | 1000 | 30.000 | 3.000 | 2.000 | 200 | 8 | 15 |
| 1032 | Truck 5T | 5000 | 100.000 | 8.000 | 5.000 | 500 | 10 | 5 |
| 1041 | Van 1T | 1000 | 30.000 | 3.000 | 2.000 | 200 | 8 | 15 |
| 1042 | Truck 5T | 5000 | 100.000 | 8.000 | 5.000 | 500 | 10 | 5 |
| 9001 | City Van 500kg | 500 | 20.000 | 2.000 | 1.500 | 150 | 8 | 60 |
| 9002 | Truck 2T | 2000 | 80.000 | 6.000 | 4.000 | 400 | 10 | 20 |

| | |
|---|---|
| Tổng capacity đội xe | 500.142.800 |
| Tổng demand | 101.484.721 |
| **demand / capacity** | **20,3%** |

> Tỉ lệ 20,3% là của **toàn corpus**. Con số ~10,5% ghi trong CLAUDE.md là của instance
> 6183 đơn — hai số không so trực tiếp được. Mỗi instance phải tự tính lại tỉ lệ này.
>
> Dù ở mức nào, kết luận vẫn giữ: load utilization thấp là **đúng**, không phải bug. Ràng
> buộc thật là **thời gian** — vận tốc trung bình 3,3 km/h ở job #21 cho thấy hơn 90% thời
> lượng route là service + chờ time window.

## Kiểm tra tính toàn vẹn — tất cả đều sạch

| Hạng mục | Kết quả |
|---|---|
| `max_duration` đơn vị giờ (≤ 24) | ✅ cả 16 loại xe, giá trị 8 hoặc 10 |
| `next_val` > `MAX(id)` | ✅ 11601>11531, 56701>56615, 301>224 |
| Cột PK SEQUENCE không `AUTO_INCREMENT` | ✅ 0 dòng vi phạm |
| `solutions.total_cost` = `DECIMAL(18,2)` | ✅ |
| Charset | ✅ `utf8mb4`, tiếng Việt nguyên vẹn, 0 ký tự thay thế |
| `service_time` | ✅ không thiếu dòng nào |
| Bảng sequence có trong `schema.sql` | ✅ cả ba |

`total_distance`, `total_co2`, `total_time` vẫn là `DECIMAL(10,2)` — trần 99.999.999,99.
Đủ cho quãng đường (km) và CO₂ (kg) ở mọi quy mô đang xét, nhưng nên để ý nếu đơn vị đổi.

## Bất thường còn tồn — chỉ ở dữ liệu test

| Loại xe | Vấn đề |
|---|---|
| id 100 `Truck 5T Small` | capacity **100** — tên nói 5 tấn, giá trị nói 100 kg |
| id 300 `Truck 10T Large` | capacity **500** — tên nói 10 tấn, giá trị nói 500 kg |

Các branch thật (`Hanoi`, `Gemadept`, `ICEL`, `Very Buge Branch`) đều nhất quán theo kg:
`Van 1T`=1000, `Truck 2T`=2000, `Truck 5T`=5000, `City Van 500kg`=500. Hai loại lệch thuộc
`Test Branch Small` và `Branch Large` — **đừng dùng hai branch đó để dựng instance**.

## Bảo mật

`branches.branch_webhook_url` là Slack incoming webhook. Branch 1000 `Branch Huge 1` **có**
giá trị trong DB. Script export không dump bảng này bằng `mysqldump` mà sinh lại
`branches_scrubbed.sql` với webhook đặt `NULL` — đã kiểm chứng cả 13 dòng đều `NULL);`.

## Cách tái lập corpus

MySQL chạy trong container Docker. Dùng `-i`, **không** dùng `-it` (`-t` cấp TTY và loại trừ
chuyển hướng stdin từ file).

```bat
set C=<ten-hoac-12-ky-tu-dau-cua-container-id>
set P=<mat_khau>
set R=docker exec -i -e MYSQL_PWD=%P% %C% mysql -u root

%R% -e "DROP DATABASE IF EXISTS gvrp_bench; CREATE DATABASE gvrp_bench;"
%R% gvrp_bench < schema.sql
%R% gvrp_bench < branches_scrubbed.sql
%R% gvrp_bench < data.sql
%R% gvrp_bench < ..\..\scripts\04_fix_sequences_after_restore.sql
```

Bước cuối **bắt buộc**. Bỏ qua thì gặp `Duplicate entry ... for key 'routes.PRIMARY'`.

## Đã loại trừ có chủ ý

- `users` — chứa hash mật khẩu.
- `branches.branch_webhook_url` — secret, đã scrub về `NULL`.
- VIEW và PROCEDURE — không code Java nào dùng, và các view **đang hỏng** (lỗi 1356) nên
  `mysqldump` cả database sẽ thất bại. Xem `../../RUNBOOK.md`.
- Dữ liệu của `solutions`, `routes`, `route_stops`, `unassigned_orders`,
  `optimization_jobs` — đây là **kết quả**. Chỉ schema được dump để app ghi được kết quả mới.
