# Instances

Một **instance** là một bài toán cụ thể, xác định đầy đủ và tái lập được. Đây là đơn vị
benchmark — không phải corpus.

## Vì sao instance là danh sách ID tường minh

`RoutePlanningRequest` nhận `List<Long> orderIds` và `List<Long> vehicleIds` **tường minh**,
không phải điều kiện lọc. Nên một instance được xác định trọn vẹn bởi hai danh sách đó cộng
`preferences`. File JSON vài chục KB — commit thoải mái, khác hẳn corpus 241 MB.

## Định dạng

⚠️ **`spring.jackson.property-naming-strategy=SNAKE_CASE`** — mọi khoá JSON gửi tới API phải
là **snake_case**, không phải camelCase. Đây là chỗ dễ mất nửa ngày nếu nhầm.

```json
{
  "_meta": {
    "instance_id": "vn_n1000_b0001_d2024-02-26",
    "corpus_sha256": "<hash cua data.sql>",
    "git_commit": "1517108",
    "created_at": "2026-07-27",
    "branch_id": 1,
    "delivery_dates": ["2024-02-26"],
    "n_orders": 1000,
    "n_vehicles": 40,
    "tong_demand": 102480.0,
    "tong_capacity": 200000,
    "demand_capacity_pct": 51.2,
    "pct_co_time_window": 80.1,
    "ghi_chu": "mot ngay, mot branch - tinh huong van hanh thuc"
  },
  "order_ids":   [101, 102, 103],
  "vehicle_ids": [9001, 9002],
  "preferences": {
    "goal": "MINIMIZE_COST",
    "speed": "NORMAL",
    "allow_unassigned_orders": false,
    "time_window_mode": "STRICT",
    "enable_pareto_analysis": false
  }
}
```

Khối `_meta` chỉ để đọc và đối soát — bỏ nó ra trước khi POST:

```
POST /api/v1/jobs/plan     -> 202 Accepted
```

## Quy tắc dựng instance

1. **Một branch duy nhất.** `submitJob` xác thực order và vehicle đều thuộc `branchId`; trộn
   branch sẽ bị từ chối.
2. **Chỉ order `status = 'SCHEDULED'`.** `COMPLETED` / `ON_ROUTE` đã qua lập tuyến.
3. **Không dùng `Test Branch Small` (100) và `Branch Large 1..3` (300–302).** Hai nhóm loại
   xe của chúng có capacity mâu thuẫn với tên (xem `../vn6183/DATASET.md`).
4. **Xác định tường minh có gộp nhiều ngày hay không** — xem mục dưới.
5. **Lấy mẫu phải tất định.** `ORDER BY id LIMIT n` sau khi đã lọc; không dùng `RAND()`.
   Cùng corpus + cùng quy tắc phải ra cùng danh sách ID.
6. **Ghi `corpus_sha256`.** Instance trỏ vào corpus nào phải rõ ràng.

## Vấn đề phương pháp: gộp nhiều ngày

`Order` có **hai** trường thời gian độc lập:

- `delivery_date` — `LocalDate`
- `time_window_start` / `time_window_end` — `LocalTime`, tức **giờ trong ngày**

Engine chỉ nhận time window dạng giây trong ngày. Nghĩa là nếu instance gộp nhiều
`delivery_date`, engine coi **toàn bộ đơn như thuộc cùng một ngày** — `delivery_date` bị bỏ
qua hoàn toàn.

Ngày đông nhất của corpus chỉ có **2.842** đơn (toàn bộ branch cộng lại). Nên:

| Mốc | Một ngày được không? | Ngữ nghĩa |
|---|---|---|
| n = 1000 | có | tình huống vận hành thật |
| n = 3000 | gần như không | phải gộp ≥ 2 ngày |
| n = 6183 | không | tổng hợp |
| n = 10000 | không | tổng hợp |

Điều này **không sai**, nhưng phải nói rõ trong luận văn: các mốc lớn là **stress test khả
năng mở rộng**, không phải kịch bản vận hành. Nếu trình bày chúng như bài toán một ngày thật,
một phản biện đọc kỹ schema sẽ chỉ ra ngay.

Trường `_meta.ghi_chu` tồn tại để buộc phải ghi rõ điều này cho từng instance.

## Neo đối chứng: tái lập job #21

`optimization_jobs.input_data` lưu nguyên request JSON của mọi job đã chạy. Lấy lại đúng tập
ID của job #21 cho phép đối chiếu trực tiếp với số đã đo:

| | |
|---|---|
| Chỉ số | Giá trị đo | Dùng làm neo được? |
|---|---|---|
| Thời gian | 13m40s | ⚠️ chỉ so được nếu cùng số luồng — mặc định hiện tại là **1** |
| Xe dùng | 958 | ✅ |
| Quãng đường | 18.058 km | ✅ |
| Cost | 264.655.468 VND | ✅ |
| Load utilization | 13,9% | ✅ công thức đúng (demand thô / capacity thô) |
| CO₂ | 3.250 kg | ❌ **không dùng được** |

**Vì sao CO₂ không dùng làm neo:** 3.250 kg trên 18.058 km ứng với ~180 g/km, nhưng dữ liệu
hiện tại có `emission_factor = 12.3` cho cả 16 loại xe — cùng quãng đường chỉ cho 222 kg,
lệch ~14,6×. Suy luận hợp lý nhất là lúc job #21 chạy, engine nhận `emissionFactor = null`
và dùng fallback 200 g/km. Chi tiết ở `../../OBJECTIVE_FUNCTION.md` mục 7.

Bốn chỉ số còn lại **không** phụ thuộc hệ số phát thải nên vẫn là neo hợp lệ. Đây vẫn là
instance giá trị nhất trong bộ — điểm duy nhất ta có số đo độc lập để xác minh harness chạy
đúng. Truy vấn 6 trong `../../scripts/05_instance_candidates.sql` lấy ra cấu trúc
`input_data`.

## Trạng thái

| Instance | Tình trạng |
|---|---|
| `vn_n1000` | ⬜ chờ `05_instance_candidates.sql` để chọn branch/ngày |
| `vn_n3000` | ⬜ |
| `vn_n6183` | ⬜ |
| `vn_n10000` | ⬜ |
| `vn_job21` (neo đối chứng) | ⬜ chờ trích từ `input_data` |
