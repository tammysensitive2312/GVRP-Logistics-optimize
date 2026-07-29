# RUNBOOK — cách chạy và ghi lại một thí nghiệm

## Trước mỗi đợt chạy

```bat
cd GVRP_Benchmark\scripts
03_verify_checksums.bat
```

Dataset lệch checksum thì **dừng lại**, đừng chạy tiếp. Kết quả sẽ không so được với các
đợt trước.

Ghi lại trạng thái code:

```bat
git rev-parse --short HEAD
git status --porcelain
```

Dòng thứ hai **rỗng** thì `git_dirty = false`. Không rỗng thì `git_dirty = true`, và bạn
phải chấp nhận rằng commit hash đó không mô tả đúng code đã thực thi. Với số liệu đưa vào
luận văn, nên commit trước rồi chạy.

## Trong lúc chạy

Một cấu hình = **tối thiểu 5 seed**. Jsprit dùng ruin & recreate ngẫu nhiên nên một lần
chạy không nói lên điều gì. Báo cáo trung bình ± độ lệch chuẩn.

Ghi mỗi lần chạy thành **một dòng** trong `results/`, theo đúng thứ tự cột ở
`results/schema.csv`. Đừng đổi schema giữa đợt — nếu buộc phải thêm cột, thêm vào cuối và
ghi rõ từ `run_id` nào trở đi cột đó mới có giá trị.

## Dấu hiệu chạy đúng trong log Engine

Thứ tự mong đợi:

```
Transport costs adapter ready
GREEN VRP built
```

Nếu dừng lâu giữa hai dòng này thì adapter ma trận đang bị bỏ qua ở đâu đó — `Jsprit` đang
tự nạp lại toàn bộ $n^2$ cặp thay vì đọc `double[][]`. Dừng và tìm nguyên nhân, đừng chờ.

## Dấu hiệu số liệu rác

| Triệu chứng | Nguyên nhân |
|---|---|
| Cost cỡ $10^{11}$ VND, hoặc quãng đường cỡ triệu km | Đã đi qua cạnh sentinel. Đếm `cost / 1e9` ra số cạnh. `assertNoSentinelEdgeTraversed()` phải bắt được — nếu không bắt thì guard đang bị tắt |
| Thuật toán dừng ở iteration 1 | `TimeTermination` thiếu `addListener()` → `startTime = 0`. Xem Bước 5 của checklist |
| `load_utilization` cao bất thường (×10) | Bug `DEMAND_SCALE`. **Đã sửa** — `SolutionMetricsCalculator:118` dùng demand thô / capacity thô, tỉ lệ đúng. Nhưng test canh gác lại **đang hỏng**, xem dưới |
| `total_co2_kg` không khớp lần chạy trước | Hệ số phát thải đổi. Xem `OBJECTIVE_FUNCTION.md` mục 7 — job #21 đo 180 g/km, dữ liệu hiện tại là 12,3 g/km |
| Route qua nửa đêm mất `arrival_time` | `parseTime` trả `null` với mốc kiểu `31:52:38`. Nút thắt là `java.time.LocalTime`, không phải cột DB (MySQL `TIME` chứa tới `838:59:59`) |
| `unassigned_orders` lệch giữa hai lần chạy cùng cấu hình | Chỉ là ngẫu nhiên của seed, hoặc `unassignedJobPenalty` chưa được set tường minh (hiện đang dùng mặc định Jsprit) |

## Test canh gác đang hỏng — sửa trước khi tin vào nó

`SolutionMetricsCalculatorTest` **không chạy được**, dù nó là test duy nhất canh cái bug
`DEMAND_SCALE`.

Nguyên nhân: một lần tìm-thay thế trong IDE đã thay chuỗi `1` thành
`TimeTerminationBugTest.java` trong các literal. Kết quả là job ID trở thành
`"order-TimeTerminationBugTest.java"`, và `SolutionMetricsCalculator:111` làm:

```java
String orderId = jobAct.getJob().getId().replace("order-", "");
Order orderDTO = context.orderDTOs().get(Long.parseLong(orderId));
```

`Long.parseLong("TimeTerminationBugTest.java")` ném `NumberFormatException`. Test đổ trước
khi tới `assertEquals`.

Cần sửa: thay mọi `TimeTerminationBugTest.java` trong literal của file đó về `1`. Chỉ sau khi
test xanh thì mới coi bug `DEMAND_SCALE` là đã được canh gác — hiện tại việc code **đúng** chỉ
được xác nhận bằng đọc, không bằng test.

Cùng loại rác tìm-thay thế: `AppConstant.java` còn dấu `-`/`+` của một merge chưa giải quyết
trong khối Javadoc của `CLUSTER_TARGET_SIZE`.

## Sau khi chạy

1. Commit file kết quả cùng với `configs/` đã dùng.
2. Nếu con số bất thường, ghi lại **cả** lần chạy bất thường đó — đừng lặng lẽ bỏ. Một dòng
   ngoại lệ có kèm giải thích giá trị hơn một bảng sạch đã được lọc.

## Cấu hình chưa thể đặt từ file

Tới khi hoàn thành Bước 4 của checklist, các tham số sau vẫn **hardcode trong code** và muốn
đổi là phải sửa `.java` rồi recompile:

| Tham số | Vị trí |
|---|---|
| `CLUSTER_TARGET_SIZE` (150) | `utils/AppConstant.java` |
| `CLUSTER_FIRST_ORDER_THRESHOLD` (1000) | `utils/AppConstant.java` |
| `CARBON_PRICE_PER_KG` (100000) | `utils/AppConstant.java` |
| `DEMAND_SCALE` (10) | `utils/AppConstant.java` |
| `FAST_REGRET`, `CONSTRUCTION` | `service/OptimizationService.java:617–618` |
| iterations default (2000), threads default (**1**) | `service/OptimizationService.java:611–612` |

`threads` mặc định là **1**, dù comment trong `OptimizationConfig` ghi 4. Mọi số wall-clock
đã đo được — kể cả 171 ms/iteration ở $n = 6183$ — đều là single-thread. Ghi rõ điều này
trong mọi bảng kết quả.
