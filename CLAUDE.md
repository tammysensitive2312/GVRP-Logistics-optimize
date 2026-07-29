# GVRP Logistics Optimize — hướng dẫn cho Claude

Hệ thống Green VRP hai tầng:

- **GVRP_Entry_API** — API tuyến đầu. Nhận yêu cầu lập tuyến, quản lý
  orders/vehicles/depots trong MySQL, đẩy job sang engine, nhận kết quả qua callback.
  Spring Boot 4 + JPA/Hibernate 6 + MySQL 8.
- **GVRP_Engine_API** — solver thuần trên Jsprit + GraphHopper. Nhận job, giải, callback
  kết quả về. **Không DB, không JPA.**

---

## Giao ước làm việc

**Chỉ đọc source. Mọi thay đổi trình bày dưới dạng diff hoặc hướng dẫn cụ thể. Chỉ ghi
vào file khi người dùng nói "làm đi" trong chính lượt đó.**

Nếu người dùng tự sửa, đọc lại file rồi mới tiếp tục — đừng giả định trạng thái cũ.

Thay đổi chạm vào DB thật (`ALTER`, `UPDATE`, `DELETE`) thì **đưa SQL cho người dùng tự
chạy**, kèm query kiểm tra trước và sau. Không tự động hoá.

Ngôn ngữ trao đổi: tiếng Việt.

## Các thư mục của dự án

```
E:\OR-tools java example\GVRP-Logistics_Optimize\      ← mount ở ĐÂY (thư mục cha)
├── GVRP_Engine_API        solver Jsprit + GraphHopper (không DB)
├── GVRP_Entry_API         API tuyến đầu, JPA/MySQL
├── GVRP_Entry_client      client HTML/JS thuần
├── GVRP_Entry_client_v2   client Angular
├── GVRP_Benchmark         datasets, scripts SQL profiling, RUNBOOK, OBJECTIVE_FUNCTION.md
└── GVRP_Document          tài liệu thiết kế (PlantUML, init.sql)
```

Thư mục cha **mount được bình thường** (ghi chú cũ nói không mount được vì có `Scheduled`
— thư mục đó không còn tồn tại). Mount ở cấp cha để Claude thấy được cả hai tầng cùng lúc,
vì phần lớn bug của dự án này nằm ở **giao diện giữa hai tầng**, không nằm trong một tầng.

## Giao ước kiểm chứng — BẮT BUỘC

Người dùng không phải chuyên gia tối ưu tổ hợp và **không có nghĩa vụ** phải hiểu suy luận
để kiểm được kết quả. Trách nhiệm chứng minh thuộc về Claude. Bốn luật dưới đây không phải
lời khuyên, là điều kiện để một phát biểu được coi là hoàn chỉnh.

### 1. Mọi công thức phải kèm một dòng thay số ngược

Đưa công thức thì phải cắm ngay số liệu đã đo và cho thấy nó tái tạo được kết quả đã biết.
Không có dòng đó thì phát biểu chưa xong.

**Tiền lệ (2026-07-28):** công thức runtime từng được viết là $T \approx 3.94\times10^{-8}
\cdot N \cdot R$ — **sai 1000×**. Cắm job #30 vào là lộ ngay:

$$3.94\times10^{-8} \times 12\,054 \times 7\,280 = 3.5 \text{ s} \quad\text{so với } 3\,419.6 \text{ s đo được}$$

Bảng số trong cùng câu trả lời thì đúng, chỉ phần chữ sai số mũ — nghĩa là **nội bộ một câu
trả lời có thể tự mâu thuẫn**, và chỉ phép thay số bắt được. Lỗi này giờ được khoá bằng
assertion trong `MeasuredLawsTest.theThousandFoldMistakeStaysDocumented()`.

### 2. Trích source phải có `file:dòng`; hành vi thư viện phải có link

Nói "code làm X" thì ghi `OptimizationService:611`. Nói "Jsprit hành xử Y" thì dẫn source
thật; không dẫn được thì nói thẳng **"chưa xác minh"** thay vì lấp bằng suy đoán nghe chắc
chắn.

### 3. Ngoại suy phải được dán nhãn, kèm dải đã đo

Con số chiếu cho 50k orders **không phải** số đo. Phải nói rõ đo trong dải nào (10k–18k), và
nếu chỉ có một điểm bất thường chưa giải thích được thì gọi kết quả là **cận dưới/cận trên**,
không phải ước lượng điểm. Bất đẳng thức phải được trình bày là bất đẳng thức.

### 4. Việc lớn phải đứng trên một con số đã kiểm

Trước khi viết code, trả lời được: *"quyết định này đứng trên con số nào, và kiểm nó thế nào?"*
Refactor block-diagonal đứng trên đúng một con số — $16 \times 50\,010^2 = 37.27$ GiB. Kiểm
con số đó **trước**, không phải sau.

### Ba dấu hiệu người dùng nên dùng để bắt lỗi

| Dấu hiệu | Ý nghĩa |
|---|---|
| Suy ngược ra **số tròn** hoặc khớp đúng giá trị DB | Gần như chắc đúng — trùng hợp kiểu đó rất khó |
| Ra số lẻ mà không có gì trong DB khớp | Bắt Claude giải thích tại sao lẻ |
| Bậc của con số nghe lạ | Yêu cầu **kiểm đơn vị**: $[\text{giây}] = [K]\cdot[\text{đơn}]\cdot[\text{tuyến}]$ nên $N R \sim 10^8$ thì $K$ phải cỡ $10^{-5}$ |

Ba câu nên nói khi nghi ngờ: **"kiểm đơn vị đi"**, **"tính lại theo cách thứ hai"**,
**"đây là bất đẳng thức hay ước lượng điểm?"**

### Nơi các phát biểu được khoá lại

- `MeasuredLawsTest` — nghiệm thu **mọi con số trong file này**: tham số suy ngược, luật
  runtime, luật prep, bảng trần bộ nhớ, bảng break-even, dominance #30/#31, các chỉ số dễ
  đọc sai. Đỏ thì hoặc tài liệu sai, hoặc code vừa đổi mà tài liệu chưa cập nhật — thông báo
  lỗi nói rõ phải sửa ở đâu. **Đừng tắt assertion, hãy cập nhật cả hai phía.**
- `BlockDiagonalCostMatrixTest` — bất biến của bố cục ma trận, so từng ô trong $n^2$.
- Guard trong code (`MatrixMemory.requireDenseFits`, guard tràn int, guard dải rộng) — nếu
  công thức sai thì guard nổ sai lúc và lộ ra.
- Reviewer độc lập: cho một agent riêng soát code mà **không** thấy suy luận của Claude. Đã
  bắt được một bug thật ở phiên đầu (offset block tính bằng `int`, cụm > 46 340 phần tử thì
  tràn và đọc lệch slot không có lỗi nào). Nên dùng trước mỗi lần merge.

**Khi thêm job mới:** thêm một dòng vào `MeasuredLawsTest.JOBS` rồi chạy lại. Job mới không
tuân luật thì test đỏ — đó chính là tín hiệu cần biết.

## Nguyên tắc kỹ thuật

- **Không suy đoán API.** Cần chữ ký một class Jsprit thì tra source thật trên GitHub, không
  đoán từ trí nhớ. Đã có tiền lệ: phải xác minh `VehicleRoutingTransportCosts`,
  `Jsprit.Builder.addConstraints`, `PrettyAlgorithmBuilder` mới kết luận được.
- **Đo trước khi kết luận.** Suy ngược từ log/số liệu thay vì phỏng đoán. Nếu chỉ có bất
  đẳng thức thì nói là bất đẳng thức, đừng biến nó thành ước lượng điểm.
- **Tính giá trị đúng trước khi gọi là bug.** Nhiều chỉ số trông vô lý lại là số đúng
  (xem "Load utilization" bên dưới) — bug thật khi đó nằm ở chỗ khác.
- **Fail-loud hơn sai âm thầm.** Ném exception ở chỗ dữ liệu vô lý, thay vì trả `null`
  hoặc log warning rồi đi tiếp. Guard tốt nêu rõ con số quan sát được, con số hợp lý, và
  cách suy ra.
- **Cẩn thận đơn vị và độ rộng cột.** Hai lỗi tốn thời gian nhất của dự án này đều thuộc
  loại đó (`max_duration` phút↔giờ, `DECIMAL(10,2)` tràn).
- Java là ngôn ngữ mặc định, JDK 21. Giữ nguyên các trường định danh cốt lõi
  (`startDepotId`, `endDepotId`) để logic định tuyến không vỡ.
- Mỗi bản vá gắn với **một** giả thuyết đã được số học xác nhận. Đừng sửa gộp — sẽ mất khả
  năng biết cái nào có tác dụng.

---

# Bẫy đã trả giá — đọc trước khi sửa

## Chung cả hai tầng

### Đơn vị `vehicle_types.max_duration` là **giờ**

DB từng lưu phút (480/600) trong khi engine nhân 3600 như giờ → xe được phép chạy 20 ngày,
ràng buộc ca làm bị vô hiệu hoàn toàn. Đã `UPDATE ... / 60`. Engine có guard ném exception
nếu `maxDuration > 24`.

Dữ liệu còn vài chỗ tên mâu thuẫn giá trị: `Truck 10T Large` capacity 500 kg,
`Truck 5T Small` capacity 100 kg.

### Outbox + idempotency: engine **sẽ** phát lại cùng một payload

`ResultSpool` ghi payload xuống `.data/results/pending/` **trước** khi gọi callback.
`CallbackRetryScheduler` quét lại theo backoff mũ. Phân loại lỗi là điểm mấu chốt:

| Phản hồi từ Entry | Engine xử lý |
|---|---|
| 2xx | `markSent()` → `sent/` |
| 4xx | `markPoisoned()` → `poison/`, không retry |
| 5xx / timeout / refused | ở lại `pending/`, retry |

Read timeout của `RestTemplate` là **300s** (mốc 10s cũ gây lỗi job #23: Entry lưu xong 10k
orders nhưng mất hơn 10s, engine bỏ cuộc và tưởng thất bại).

Phía Entry, `handleCompletion` **bắt buộc** kiểm `solutionRepository.findByJobId` ngay đầu,
vì có hai tình huống phát lại: (1) lần trước Entry trả 5xx; (2) lần trước Entry xử lý
**xong** nhưng phản hồi về sau read timeout. Không có chốt đó thì mỗi retry nhân đôi
solution + routes + route_stops.

Guard trạng thái phải chấp nhận cả `FAILED`, vì chính khối `catch` của phương thức này đánh
dấu job `FAILED` khi lần xử lý trước hỏng. Chỉ cho `PROCESSING` thì bản phát lại vĩnh viễn
không vào được.

### Mã HTTP Entry trả về cho engine mang ngữ nghĩa retry

| Trạng thái | Trả về | Engine làm gì |
|---|---|---|
| Đã có solution | 200 (return lặng lẽ) | `markSent`, ngừng retry |
| `COMPLETED` / `CANCELLED` | 200 | ngừng retry |
| `PENDING` | **503** qua `JobNotReadyException` | giữ `pending/`, retry sau ~60s |
| Lỗi xử lý thật | 500 | giữ `pending/`, retry |
| Payload sai | 4xx | chuyển `poison/`, **không** retry |

Đừng bao giờ trả 200 cho tình huống "chưa xử lý được nhưng sau này có thể" — engine sẽ đánh
dấu đã giao và kết quả vĩnh viễn không vào DB trong khi log báo thành công.

Endpoint hỗ trợ: `GET /{jobId}/result`, `POST /{jobId}/resend`.

### Jackson 3, không phải `com.fasterxml`

Spring Boot 4 → Jackson 3. Bean khai trong `WebConfig` là
`tools.jackson.databind.json.JsonMapper`, không phải
`com.fasterxml.jackson.databind.ObjectMapper`. Annotation thì vẫn ở
`com.fasterxml.jackson.annotation` (Jackson 3 cố tình giữ nguyên). `pom.xml` còn khai
`jackson-databind` (Jackson 2) thừa, không file nào dùng.

Jackson 3 chuyển exception sang **unchecked**. Đừng bọc lời gọi Jackson bằng
`catch (IOException)` đơn lẻ — sẽ lỗi "exception is never thrown".

---

## GVRP_Engine_API

### Ma trận khoảng cách: block-diagonal, KHÔNG phải `double[n][n]`

Lịch sử ba bước, đừng lùi bất kỳ bước nào:

1. `Map<String, Entry>` — ~7 GB ở $n = 6183$. Bỏ.
2. `double[n][n]` dày — $M(n) = 16n^2$; $n = 50\,010$ cần **37.27 GiB** → job 50k treo
   (GC thrash, **không** có `OutOfMemoryError`). Bỏ.
3. `BlockDiagonalCostMatrix` (hiện tại) — chỉ lưu ô mà `MatrixMask.needed()` cho phép:
   $N \cdot S + 2 D n$ ô. Ở $N = 50\,000$, $S = 150$, $D = 10$: **135.8 MB**, giảm 294×.
   Bộ nhớ là $O(N \cdot S)$ — **tuyến tính**.

Bài học cốt lõi: **prune giảm TÍNH TOÁN, không giảm BỘ NHỚ.** Mask cũ prune 99.66% số cặp
nhưng tiết kiệm 0 byte, vì ô bị prune vẫn chiếm đủ 8 byte trong mảng dày. Muốn giảm bộ nhớ
phải đổi *cách lưu*, không phải *cách tính*.

Bất biến bắt buộc: tập $\{(i,j) : \text{needed}(i,j)\}$ phải **trùng khớp** tập ô mà
`BlockDiagonalCostMatrix` có chỗ lưu. Sửa `MatrixMask.needed()` thì phải sửa cả bố cục
block. `BlockDiagonalCostMatrixTest` kiểm bất biến này từng ô trong $n^2$.

Getter cho cặp khác cụm phải **trả sentinel, không throw** — `NoPrunedEdgeConstraint` và
`assertNoSentinelEdgeTraversed` dựa vào việc *đọc được* sentinel để hoạt động.

`MatrixBasedTransportCosts` là adapter zero-copy tra qua `Location.getIndex()`, index gán
trong `prepareContext`. Tuyệt đối không dùng `allLocations().indexOf()` — $O(n)$ mỗi lần
tra, từng gây ~38 triệu phép so sánh cho mỗi lần tính metric. Không dùng
`matrixBuilder.build()` của Jsprit: nó nạp lại toàn bộ $n^2$ cặp và gây treo.

Nhánh dày (`DenseCostMatrix`) vẫn còn cho job nhỏ / Pareto, nhưng giờ **fail-loud**: vượt
trần thì ném `IllegalStateException` kèm $n_{\max}$ thay vì treo.

### Cạnh sentinel

Ô bị prune mang `MatrixMask.PRUNED_METERS = 1e9` m. `NoPrunedEdgeConstraint` (priority
`CRITICAL`, **luôn** được áp) chặn solver chèn job qua cạnh này — order không tới được sẽ
thành `UNASSIGNED`, đúng ngữ nghĩa. `assertNoSentinelEdgeTraversed` chạy sau khi giải để
chặn cost rác chảy xuống DB.

Nếu thấy cost cỡ `1e11` VND hoặc quãng đường cỡ triệu km: đếm `cost / 1e9` ra số cạnh
sentinel bị đi qua.

### `CLUSTER_TARGET_SIZE`

Đang là **150**. Số cụm $C = \lceil N / 150 \rceil$; tỉ lệ prune $\approx 1 - 1/C$. Giá trị
cũ 800 chỉ tạo 2 cụm ở $N \approx 1200$ nên prune chỉ 41% — chậm mà không lợi.

Trần trên của một cụm là **46 340** phần tử: offset trong block tính bằng `int`, nên
$s^2 > $ `Integer.MAX_VALUE` sẽ quấn vòng và đọc lệch slot mà không có lỗi nào. Đã có guard.

### Jsprit bật core constraints theo mặc định

`Jsprit.Builder.addConstraints = true`, nên time window / load / skills **có** được áp qua
`PrettyAlgorithmBuilder.addCoreStateAndConstraintStuff()` kể cả khi truyền
`ConstraintManager` của riêng mình. Nếu một ràng buộc có vẻ bị bỏ qua, nghi **dữ liệu**
trước khi nghi solver.

### `maxIterations = 2000` là hằng số, bất kể quy mô

Đây là nguyên nhân nghịch lý "siết fleet lại cho kết quả tốt hơn". Job #30 và #31 cùng
12 054 orders; #31 (fleet cap 5 000) **dominate** #30 (cap 10 000) trên cả ba trục:
distance −30%, vehicles −34%, cost −32%. Lời giải của #31 là *feasible* trong instance của
#30 (fleet là tập con), nên #30 đơn giản là **bị bỏ dở** — 2000 iterations không đủ khi có
~7 000 route hở.

Số route $R$ vào runtime **tuyến tính**, nên fleet cap vừa là đòn chất lượng vừa là đòn
tốc độ. Nếu `vehiclesUsed / fleetCap > 96%` thì cap đang **bind** — nghiệm bị ép, chưa phải
tối ưu.

### Hàm mục tiêu: ba loại "cost" khác nhau đang cùng tồn tại

1. **Cost solver thật** — `11.0·d_m + 0.972·t_s + 70000·R` (với weights mặc định 0.7/0.3).
2. **Cost báo cáo** (`SolutionMetricsCalculator`) — dùng tham số *thô*, không nhân weight,
   và **không** bao gồm CO2 cost.
3. **CO2 cost** — in ra riêng, và ở tham số hiện tại nó **lớn hơn** tổng cost.

Khi benchmark, phải nói rõ đang so cái nào. Ba khiếm khuyết cấu trúc đã xác nhận bằng số:

- **CO2 cộng tuyến hoàn hảo với distance.** `CO2 = 0.18 × distance` đúng ở cả 4 job, không
  phụ thuộc tải/tốc độ/loại xe ⇒ Pareto front cost↔CO2 là một **điểm**, không phải đường
  cong. Đây là khiếm khuyết **mô hình**, không phải giá trị tham số — thay số không chữa
  được, phải làm CO2 phụ thuộc tải hoặc tốc độ.
- **Weights nhân bất đối xứng.** `fixed` và `time` chỉ nhân `costWeight`, còn `distance`
  nhân cả hai. Break-even detour để mở xe mới $= \text{fixed}\cdot w_c / \text{dist}$:

  | preset | dist (VND/m) | fixed | break-even |
  |---|---|---|---|
  | COST_FOCUSED (1, 0) | 8.00 | 100 000 | 12 500 m |
  | mặc định (0.7, 0.3) | 11.00 | 70 000 | 6 364 m |
  | BALANCED (0.5, 0.5) | 13.00 | 50 000 | 3 846 m |
  | ECO_FOCUSED (1e-4, 1) | 18.00 | 10 | **0.56 m** |

  ECO_FOCUSED/PURE_ECO sinh nghiệm suy biến 1 đơn/xe. Có log cảnh báo khi break-even < 500 m.
- **`fixed_cost = 100 000` VND/chuyến quá rẻ** — chỉ tương đương 12.5 km xăng, trong khi
  quãng đường/order thực tế là 2.6–9.4 km. Phân mảnh fleet do đó *một phần* là nghiệm kinh
  tế đúng của mô hình. Thực tế xe 5T (tài xế + khấu hao) nên cỡ 500k–1M.

### `emission_factor` không tới được engine

Log cho **180.0 g/km cố định ở mọi job và mọi loại xe** — đúng giá trị hardcode
`VehicleFeaturesDTO.defaultFeatures()` (PETROL_CAR). Trong khi DB ghi 12.3, và quy tắc theo
capacity của `init.sql` cho `DIESEL_TRUCK = 280`. Tức JSON `vehicle_features` đang bị bỏ
qua và mọi xe tải 5T bị tính CO2 như xe con.

Lưu ý: cả `CARBON_PRICE_PER_KG = 100 000` và `emission_factor = 12.3` đều là **số giả** do
người dùng đặt tạm khi chưa có giá thật. Đừng suy luận gì từ giá trị của chúng — nhưng
việc *cùng một giá trị 180 xuất hiện ở mọi loại xe* thì vẫn là bug thật.

### Bug định dạng thời gian: `toMinutesPart()` ăn mất giờ

`OptimizationService` in `"in {}m {}s"` với `d.toMinutesPart()` — hàm này trả phút **trong
giờ** (0–59). Job #33 chạy 1h 10m 52.9s được in là `10m 52s`. Job #30 in `12m 34s` trong
khi riêng jsprit đã `took 3419.576 seconds` (57m) — bất khả, thực tế là 1h12m34s.

Nguồn "ảo" thứ hai: `took X seconds` của jsprit **chỉ** tính pha search. Phần chênh
(prepareContext + K-means + build matrix) chiếm 21–41% wall time mà không có dòng log nào.
Khi đọc thời gian job, luôn tách **prep** và **search** riêng.

### Vùng chưa có checkpoint cancel

`buildGreenVRP` → `vrpBuilder.build()`.

---

## GVRP_Entry_API

### Sinh khoá chính: SEQUENCE và AUTO_INCREMENT không được sống chung

`Route`, `RouteStop`, `UnassignedOrder` dùng `GenerationType.SEQUENCE` với
`allocationSize = 50`. MySQL 8 không có SEQUENCE thật nên Hibernate giả lập bằng bảng
`route_sequence` / `route_stop_sequence` / `unassigned_order_sequence`.

Các cột PK này **không được để `AUTO_INCREMENT`**. Nếu để, MySQL sẽ tự phát ID sau lưng
Hibernate và sớm muộn đụng vào dải mà sequence sẽ cấp — đã gây
`Duplicate entry '1010' for key 'routes.PRIMARY'`.

Khi seed dữ liệu hoặc khôi phục backup, phải kéo `next_val` lên trên `MAX(id)` (dư +1000
cho an toàn — ngữ nghĩa `next_val` phụ thuộc optimizer `pooled`/`pooled-lo`, vượt dư chỉ tốn
ID, vượt thiếu thì vỡ).

**Không** đổi các entity này về `IDENTITY`: nó vô hiệu hoá JDBC batch insert
(`batch_size=50`, `order_inserts=true`) và giết hiệu năng ở job 10k orders.

### Độ rộng cột tiền tệ

`solutions.total_cost` là `DECIMAL(18,2)`. Bản `DECIMAL(10,2)` cũ chỉ chứa được
99.999.999,99 và tràn ở job 6183 orders (cost thật 264.655.468 VND). `entity/Solution.java`
và `resources/database/GVRP_master_initial.sql` phải khớp nhau.

`ddl-auto=update` **không** đổi kiểu cột đã tồn tại — chỉ thêm bảng/cột thiếu. Mọi thay đổi
kiểu phải `ALTER` tay.

Ở quy mô 12k–18k orders cost đã lên 1.78e9 VND — vẫn trong `DECIMAL(18,2)`, nhưng nếu có cột
tiền nào còn `DECIMAL(10,2)` thì nó đã tràn từ lâu.

### `parseTime` và mốc thời gian vượt 24 giờ

`OptimizationCallbackService.parseTime` nuốt lỗi và trả `null` khi engine gửi mốc kiểu
`31:52:38`. Nút thắt là `java.time.LocalTime` (0–23:59:59), **không phải** cột DB — MySQL
`TIME` chứa tới `838:59:59`. Route qua nửa đêm sẽ mất giờ đến/đi mà không báo gì.

Chưa sửa. Cách sửa phụ thuộc quyết định: lưu "thời điểm trong ngày" hay "độ trễ tính từ đầu
ca".

### Race condition khi submit job (chưa chữa gốc)

`OptimizationJobService.submitJob` tạo job ở `PENDING`, gọi engine trong `afterCommit`.
`EngineApiClientImpl.submitOptimizationAsync` POST **trước**, set `PROCESSING` **sau** khi
nhận 202. Job nhỏ giải xong trước thời điểm đó → callback về lúc DB còn `PENDING`.

Hiện chữa triệu chứng bằng `JobNotReadyException` → 503 → engine retry. Chữa gốc là tạo job
thẳng ở `PROCESSING` trong `submitJob` (chưa áp dụng).

---

# Số liệu đã đo

Nguồn: job #30–#33 (2026-07-27/28), vehicle type `Truck 5T` (capacity 5000, fixed 100 000,
8 000 VND/km, 5 000 VND/h, maxDuration 10 h).

| job | orders | vehicles | jsprit | prep | distance | cost | load util | time util |
|---|---|---|---|---|---|---|---|---|
| 30 | 12 054 | 7 280 | 3 419.6 s | ~934 s | 112 917 km | 1.779e9 | 3.4% | 40.7% |
| 31 | 12 054 | 4 813 | 2 292.9 s | ~1 102 s | 78 674 km | 1.213e9 | 5.2% | 42.7% |
| 32 | 10 054 | 2 890 | 1 150.3 s | ~807 s | 50 644 km | 7.613e8 | 7.1% | 46.5% |
| 33 | 18 054 | 2 946 | 2 870.4 s | 1 382 s | 47 870 km | 7.600e8 | 12.6% | 56.0% |

**Chỉ #30 vs #31 là so sánh có kiểm soát** (cùng bộ order). #32/#33 khác dataset, khác mật
độ (**5.04 vs 2.65** km/order) — đừng dùng chúng làm bằng chứng cho tác dụng của fleet cap.
Mật độ đầy đủ: #30 = 9.37, #31 = 6.53, #32 = 5.04, #33 = 2.65 km/order.

## Luật runtime search

$$T \approx 3.94\times10^{-5} \cdot N \cdot R \text{ giây (2000 iters, 1 thread)}$$

Job #30/#31/#32 lệch nhau **1.6%**. Job #33 cho $5.40\times10^{-5}$ (+37%, chạy trên JVM
khác — chưa giải thích được, nên coi hằng số trên là **cận dưới**). Dạng $N \cdot R$ đúng
như regret insertion quét mọi route hở: kiểm chứng phụ — $T_{30}/T_{31} = 1.49$ so với
$R_{30}/R_{31} = 1.51$.

Chiếu cho $N = 50\,000$: $R = 8000 \Rightarrow$ 4.38 h (1 thread) / ~1.1 h (8 thread);
$R = 3000 \Rightarrow$ 1.64 h / ~0.4 h.

## Luật prep (matrix + K-means) — TUYẾN TÍNH

| N | prep | ms/order |
|---|---|---|
| 10 054 | 807 s | 80.3 |
| 12 054 | 934 s | 77.5 |
| 12 054 | 1 102 s | 91.4 |
| 18 054 | 1 382 s | 76.5 |

$\approx 81$ ms/order, tuyến tính vì số call GraphHopper $= N \cdot S$ với $S$ cố định.
Chiếu $N = 50\,000$: **~4 070 s ≈ 1.13 h**. Prep **không** phải nút thắt.

`DistanceMatrixService` in `ms/location` ở dòng `[Matrix] XONG` để mỗi job tự cập nhật hằng
số này.

## Trần bộ nhớ

$$M_{\text{dày}}(n) = 16n^2 \qquad n_{\max} = \sqrt{\frac{\text{heap} \times 0.6}{16}}$$

| N | n | dày | block ($S{=}150$) |
|---|---|---|---|
| 10 054 | 10 064 | 1.51 GiB | ~27 MB |
| 18 054 | 18 064 | 4.86 GiB | ~49 MB |
| 50 000 | 50 010 | **37.27 GiB** | **135.8 MB** |

| `-Xmx` | $n_{\max}$ dày |
|---|---|
| 8g | 17 947 |
| 12g | 21 981 |
| 16g | 25 381 |
| 64g | 50 763 |

Job #33 chạy được ở $n = 18\,064$ ⇒ heap thực khoảng 8–12 GiB. Với block-diagonal, trần
mới là $N_{\max} = 0.6H/(16S) \approx$ 2.1 triệu order ở heap 8 GiB — nút thắt chuyển sang
object graph của Jsprit.

## Chỉ số dễ đọc sai

**`Load util` là load (demand/capacity), KHÔNG phải thời gian.** Nó bằng đúng
$2.05\% \times$ (orders/vehicle) ở cả 4 job ⇒ hoàn toàn xác định bởi mật độ đơn, không phải
bởi chất lượng nghiệm. Job #31: tổng demand ~1.25 tr kg ⇒ sàn theo capacity chỉ **250 xe**,
sàn theo thời gian là **2 055 xe**, đang dùng 4 813. **Ràng buộc chặn là thời gian, không
phải tải** — nên load util thấp là *đúng*, và báo cáo nó như KPI chính là sai chiều.

**Vận tốc trung bình 2.90–3.83 km/h** (job 33 / job 31) — chậm hơn người đi bộ. Suy luận
định tính: phần lớn thời lượng route là service + chờ time window, không phải chạy xe, nên
phần lớn `timeCost` (5 000 VND/h) đang trả cho xe đứng chờ. **Chưa lượng hoá được** tỉ lệ
chính xác vì log không in service time — đừng trích một con số phần trăm cho điều này.

---

# Còn tồn đọng

## Engine — ba đòn rẻ chưa làm (commit riêng từng cái)

- `numThreads` default = **1** (`OptimizationService:611`). Đang bỏ không 4–8× tốc độ.
- `buildJspritVehicle` tra depot bằng `allLocations().stream().filter().findFirst()` —
  quét tuyến tính, **2 lần mỗi xe**. Ở 8 000 xe × 50 010 locations = **800 triệu** vòng
  `String.equals`. Thay bằng `Map<String, Location>` dựng một lần.
- `buildGreenVehicleType` gọi **trong vòng lặp xe** → tạo 8 000 `VehicleTypeImpl` giống
  nhau thay vì 2. Hoist ra ngoài, cache theo `vehicleTypeId`.

## Engine — khác

- `maxIterations` nên scale theo $N$ (hoặc theo thời gian). Hiện là hằng 2000 bất kể quy mô.
- Block timeout ở `OptimizationService:647-654` đã **bị comment sạch**, `timeoutSeconds` là
  dead config, nhưng log vẫn in `Timeout: {}s` như thể còn hiệu lực.
- `BatchGraphHopperMatrixProvider` có trong repo nhưng **không** có annotation Spring và
  không được `@Qualifier` trỏ tới → đang chết. Nếu nối được, đây là cách cắt 4 070 s prep.
- Harness nghiệm thu: chưa đối chiếu block vs dense **trên nghiệm thật** (mục tiêu: không
  rớt đơn, gap cost ≤ 1%). Đã có test đối chiếu từng ô ở tầng ma trận, chưa có ở tầng nghiệm.
- Chưa có điểm đo ở **25k orders** để biết hằng số runtime có tiếp tục trôi lên như job #33
  hay không.
- Chưa có test hồi quy cho sentinel-edge, progress, cancel, outbox.
- `PRUNED_METERS = 1e9` thổi phồng thang penalty nội bộ Jsprit (không sai kết quả, chỉ có
  thể kém tối ưu).
- Mô hình CO2 cần phụ thuộc tải hoặc tốc độ, nếu không thì mục tiêu "green" chỉ là trang trí.
- `DistanceMatrix.get(i,j)` cắt phần lẻ giây (`Duration.ofSeconds((long) t)`) — ba benchmark
  test rebuild `double[][]` qua đây nên mất độ chính xác dưới 1 s. Đường nóng không ảnh hưởng.
- Dọn dẹp: import thừa `WebConfig.java:11`, dependency Jackson 2 thừa trong `pom.xml`.

## Entry

- Chưa chữa gốc race condition ở `submitJob` (ưu tiên thấp).
- `parseTime` vẫn mất dữ liệu với route qua nửa đêm.
- 20% orders không có time window.
- Chưa có test hồi quy cho idempotency callback, `JobNotReadyException`, progress, cancel.
- Dead code: `OptimizationCallbackService.markJobFailed` (đã bị `jobStatusUpdater` thay thế);
  `routeRepository` và `routeStopRepository` không còn được dùng.

---

# Chạy và kiểm thử

```bash
cd GVRP_Engine_API && ./mvnw -o compile && ./mvnw -o test
cd GVRP_Entry_API  && ./mvnw -o compile && ./mvnw -o test
```

Cần **JDK 21**.

Dấu hiệu engine chạy đúng trong log:

1. `[Matrix] Bố cục BLOCK ... giảm N×` — nếu thấy `Bố cục DÀY` ở job lớn thì cluster-first
   không bật, và nó sẽ ném exception thay vì treo.
2. `Transport costs adapter ready` rồi **ngay sau đó** `GREEN VRP built` — dừng lâu giữa hai
   dòng này nghĩa là adapter đang bị bỏ qua ở đâu đó.
3. `Cost weight: X, CO2 weight: Y` — luôn ghi lại dòng này khi so sánh hai job, vì thay đổi
   weights làm break-even detour biến động 20 000× (xem bảng preset ở trên).
