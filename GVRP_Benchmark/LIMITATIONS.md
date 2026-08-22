# Giới hạn đã biết của hệ thống — ghi cho benchmark & luận văn

> Mỗi mục ở đây là một **limitation có chủ đích hoặc đã được đo**, không phải bug chờ sửa.
> Mục đích: khi trình bày kết quả, biết trước con số nào là "đúng nhưng gây bất ngờ" để
> giải thích chủ động, thay vì bị phản biện chỉ ra.
>
> Cập nhật 2026-08-25, commit `38425a8`.

---

## L1. Cluster-first cô lập đơn ở ranh giới cụm → một phần đơn UNASSIGNED

### Hiện tượng

Với job ≥ `CLUSTER_FIRST_ORDER_THRESHOLD` (1000) đơn, pipeline cluster-first bật. Một số
đơn nằm ở **ranh giới giữa các cụm** bị đánh dấu `UNASSIGNED` dù đội xe còn thừa năng lực.

Đo được trên instance `vn_n1000` (1000 đơn, một branch, một ngày): **đúng 100/1000 đơn
(10%) rớt**, và con số này **bất động** qua các cấu hình chi phí khác nhau (xem bằng chứng
bên dưới).

### Cơ chế — vì sao đây là đánh đổi, không phải lỗi

Kiến trúc là **Fisher-Jaikumar (cluster-first, route-second)**: K-means chia đơn thành C
cụm theo toạ độ, `VehicleClusterAssigner` gán mỗi xe vào đúng một cụm, rồi Jsprit chỉ giải
trong từng cụm. Hai ràng buộc **cứng** thực thi ranh giới:

- `ClusterRouteConstraint` (`HardRouteConstraint`) — chặn xe cụm A phục vụ đơn cụm B.
- `NoPrunedEdgeConstraint` (priority CRITICAL) — cạnh xuyên cụm mang sentinel
  `MatrixMask.PRUNED_METERS = 1e9` m, nên insertion qua cạnh đó là bất khả thi.

Hệ quả: nếu K-means xếp một đơn vào cụm mà **không xe nào trong đúng cụm ấy phủ tới** (do
`max_distance` của xe, hoặc thuần tuý đơn nằm lệch rìa cụm), đơn đó **không có đường sang
cụm khác** — nó rơi về `UNASSIGNED`, đúng ngữ nghĩa "order không tới được".

Nhấn mạnh để nói cho chuẩn: **không phải "hết xe"**. Ở `vn_n1000`, mỗi cụm có ~428 xe —
thừa xe. Đơn rớt vì bị **khoá cứng trong cụm**, không phải vì thiếu năng lực. Đây là đánh
đổi cố hữu của cluster-first: nó hi sinh tính tối ưu toàn cục (một solver phẳng có thể ghép
đơn ranh giới sang xe cụm lân cận) để đổi lấy khả năng mở rộng (không phải dựng ma trận
$n^2$, xem `ENGINE_V1_TO_V2.md`). Đánh đổi này có trong văn liệu VRP; nó là **đặc tính**
của phương pháp, không phải khiếm khuyết cài đặt.

### Bằng chứng số — cặp đối chứng #39 vs #40

Cùng 1000 đơn, cùng branch/ngày, **chỉ khác `fixed_cost`** (sửa trong DB). Đây là so sánh
có kiểm soát.

| | job #39 | job #40 |
|---|---|---|
| fixed_cost (tổng) | 11.045.000 | 106.800.000 (×9,7) |
| Vehicles used | 712 | 583 (−18%) |
| Distance | 11.081 km | 8.603 km (−22%) |
| Fuel cost | 9.815.294 | 11.526.665 (+17%) |
| Total cost (báo cáo) | 24.305.586 | 121.887.925 (×5,0) |
| **Served / Unassigned** | **900 / 100** | **900 / 100** |
| Load util | 60,3% | 63,4% |

Đọc bảng này:

1. **Nâng `fixed_cost` giảm số xe (712→583) và quãng đường (−22%)** — đòn kinh tế hoạt động
   đúng lý thuyết break-even. Fuel cost *tăng* trong khi distance *giảm* vì đội xe dịch từ
   "nhiều xe nhỏ" sang "ít xe to" (cost_per_km cao hơn) — đúng hành vi mong muốn.
2. **`900/100` bất động tuyệt đối.** Đổi chi phí, mọi thứ khác dịch chuyển, nhưng đúng 100
   đơn vẫn rớt. Đây là bằng chứng quyết định: nếu đơn rớt vì lý do *kinh tế* (thiếu xe / xe
   đắt) thì đổi chi phí phải làm con số này nhúc nhích. Nó bất động ⇒ đơn rớt vì lý do
   **cấu trúc** (ràng buộc cứng của cluster), không phải kinh tế.
3. Cả hai job dùng cùng seed K-means (42, hardcode) ⇒ cùng một phân cụm ⇒ **cùng đúng 100
   đơn** bị cô lập. Đó là lý do con số trùng khít, không phải trùng hợp.

### Cảnh báo khi báo cáo `total_cost`

`total_cost` báo cáo dùng `fixed_cost` **thô** (`SolutionMetricsCalculator`), nên nó phồng
theo tham số ta vừa đổi: #40 gấp 5× #39 **không** nghĩa là #40 tệ hơn — định tuyến của #40
tốt hơn thật (distance −22%). **So chất lượng nghiệm bằng `distance` + `vehicles_used`,
không bằng `total_cost`** khi hai run khác `fixed_cost`. Vì lý do này, `fixed_cost` phải là
một cột trong `results/schema.csv` (nếu không, hai dòng lệch 5× mà không biết vì sao).

### Vì sao không "sửa"

- Tăng `unassignedJobPenalty` không giúp: ràng buộc là **hard**, không phải soft penalty —
  solver không được phép vi phạm bằng cách trả thêm tiền.
- Cho thêm xe không giúp: cụm đã thừa xe.
- Cách chữa thật là **cho phép đơn ranh giới vượt cụm** (nới `ClusterRouteConstraint`, hoặc
  gán đơn vào ≥2 cụm gần nhất thay vì đúng 1). Nhưng điều đó làm loãng chính lợi ích prune
  của cluster-first — phải cân bằng, và cần benchmark riêng. Chưa làm; ghi nhận là hướng
  phát triển, không phải sửa lỗi.

### Cách giảm nhẹ tạm thời (nếu cần phục vụ hết đơn)

- Hạ số đơn xuống dưới ngưỡng 1000 → cluster-first tắt → solver phẳng, unassigned về gần 0.
  Chỉ khả thi ở quy mô nhỏ.
- Chỉnh `CLUSTER_TARGET_SIZE` (hiện 150): cụm to hơn = ít ranh giới hơn = ít đơn cô lập hơn,
  đổi lại prune ít đi. Cần đo prune% / unassigned / build-time để chọn.

---

## L2. `strictTimeWindows` (FLEXIBLE) — cấu hình có, engine không đọc

`RoutePlanningRequest.timeWindowMode` (STRICT/FLEXIBLE) được `OptimizationConfigMapper` ánh
xạ thành `config.strictTimeWindows` và gửi xuống engine. **Nhưng engine không đọc field này
ở đâu cả** — `buildJspritService` luôn gắn `TimeWindow.newInstance(start, end)` cứng cho mọi
đơn có time window, bất kể STRICT hay FLEXIBLE.

Hệ quả: đổi `time_window_mode` sang FLEXIBLE **không thay đổi kết quả**. Time window trong
engine luôn là hard constraint (Jsprit bật core constraints mặc định).

Đây một phần góp vào L1: time window cứng cũng có thể khiến đơn không xe nào phục vụ kịp giờ
bị rớt. Không tách được đóng góp của time window khỏi đóng góp của cluster bằng cách bật
FLEXIBLE, vì nút đó chết.

**Cách nối (chưa làm):** Jsprit 1.8 không có soft time window sẵn. Đơn giản nhất là khi
`strictTimeWindows=false` thì **bỏ hẳn** `setTimeWindow()` (giao giờ nào cũng được — không
phải soft đúng nghĩa, nhưng đủ để chẩn đoán và cho một chế độ "bỏ qua giờ"). Soft-TW thật
(phạt lệch giờ thay vì cấm) cần đụng `VehicleRoutingActivityCosts` — thay đổi kiến trúc cost,
chưa xác minh API.

---

## L3. Seed của Jsprit không kiểm soát được từ cấu hình

`OptimizationService.createAlgorithm` set ITERATIONS / THREADS / FAST_REGRET / CONSTRUCTION
nhưng **không** set random seed cho Jsprit. Jsprit tự sinh seed mỗi lần chạy.

Hệ quả cho benchmark: cột `seed` trong `results/schema.csv` **không điền đúng được** — không
control được trước, không đọc ra được sau. Luật "5 seed mỗi cấu hình" của RUNBOOK chưa thực
thi được cho tới khi thêm đường truyền seed xuống Jsprit.

(Seed K-means thì cố định = 42L, hardcode — phân cụm tái lập được; đây là seed *khác*, không
liên quan.)

**Cách nối (chưa làm):** Jsprit 1.8 nhận seed qua `Jsprit.Builder.setRandom(new Random(seed))`
— **CHƯA XÁC MINH** chữ ký, cần tra source jsprit/jsprit trên GitHub trước khi viết code.

---

## L4. `maxIterations` là hằng số, không scale theo quy mô

`maxIterations` mặc định 2000 cho mọi N. Đã đo (xem `CLAUDE.md`): job #30 (12.054 đơn, fleet
10.000) bị **dominate** bởi #31 (cùng đơn, fleet 5.000) trên cả ba trục — nghĩa là #30 bị
**bỏ dở** vì 2000 vòng không đủ khi có ~7.000 route hở. Với instance ≥ 12k, 2000 vòng là
thiếu; nghiệm chưa thoát cực tiểu cục bộ.

Với `vn_n1000` thì 2000 vòng thừa đủ — limitation này chỉ cắn ở quy mô lớn.

**Cách nối (chưa làm):** cho `maxIterations` scale theo N (hoặc theo thời gian). Hệ số phải
đo, không đoán. Runtime tuyến tính $T \approx 3{,}94\times10^{-5}\,N\,R$ giây (1 thread) nên
tăng iterations tăng runtime tương ứng — cân nhắc bật đa luồng (`numThreads`, hiện xuống
solver = 4) để bù.

---

## Bảng tóm tắt

| # | Limitation | Ảnh hưởng | Quy mô cắn | Trạng thái |
|---|---|---|---|---|
| L1 | Cluster-first cô lập đơn ranh giới | ~10% UNASSIGNED ở `vn_n1000` | ≥ 1000 đơn | Đánh đổi cố hữu, ghi nhận |
| L2 | FLEXIBLE không được engine đọc | time window luôn cứng | mọi quy mô | Chưa nối |
| L3 | Seed Jsprit không kiểm soát | không tái lập seed | benchmark nhiều seed | Chưa nối |
| L4 | maxIterations hằng số | nghiệm bỏ dở | ≥ 12k đơn | Chưa nối |
