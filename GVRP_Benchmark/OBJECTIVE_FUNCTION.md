# Hàm mục tiêu — phân tích và đề xuất chuẩn hoá

> Bước 3 của checklist hạ tầng thí nghiệm. Soạn 2026-07-27, trên commit `1517108`.
>
> Kết luận ngắn: **mô hình "green" hiện tại không tạo ra sự đánh đổi nào.** Không phải vì
> code sai, mà vì cả 16 loại xe đều có cùng một hệ số phát thải. Đó là phát hiện quan
> trọng nhất trong tài liệu này.

---

## 1. Sáu phát hiện, xếp theo mức độ

| # | Phát hiện | Mức độ |
|---|---|---|
| 1 | **Cả 16 loại xe có `emission_factor = 12.3` giống hệt nhau** → CO₂ tuyến tính hoàn toàn với quãng đường → "đa mục tiêu" là ảo, Pareto frontier suy biến | Nghiêm trọng — làm mất ý nghĩa chữ "Green" |
| 2 | **Solver và báo cáo dùng hai định nghĩa THỜI GIAN khác nhau.** Solver chỉ tính thời gian di chuyển; báo cáo tính toàn bộ thời lượng kể cả service + chờ. Lệch ~9× | Nghiêm trọng — lớn hơn lệch CO₂ |
| 3 | `emission_factor = 12.3` gần như chắc chắn là **lít/100 km** bị dùng như **g/km** — lệch ~27× | Cao |
| 4 | `CARBON_PRICE_PER_KG = 100.000` VND/kg ≈ **46× giá EU ETS** hiện tại | Cao |
| 5 | Báo cáo **không** cộng CO₂ trong khi solver **có** tối ưu nó | Trung bình |
| 6 | `VehicleFeaturesService.getEmissionFactor` trả **`0.0`** thay vì `null` khi thiếu dữ liệu → guard mặc định 200 của engine không bao giờ chạy → phần "green" tắt âm thầm | Trung bình — fail-silent |

Hệ quả kéo theo: **job #21 không tái lập được** với dữ liệu hiện tại (mục 7).

---

## 2. Tầng solver — công thức Jsprit thực sự tối ưu

Nguồn: `MatrixBasedTransportCosts.getTransportCost` (dòng 57–63) và
`GreenVRPCostCalculator.buildGreenVehicleType` (dòng 86–136).

Adapter tái tạo đúng ngữ nghĩa `VehicleRoutingTransportCostsMatrix` của Jsprit:

$$
c(i,j,\text{veh}) = p_{\text{dist}} \cdot d_{ij} + p_{\text{time}} \cdot t_{ij}
$$

với $d_{ij}$ tính bằng **mét**, $t_{ij}$ bằng **giây**, và các tham số lấy từ vehicle type:

$$
p_{\text{dist}} = \underbrace{\frac{c^{km}}{1000}}_{\text{VND/m nhiên liệu}} w_c
\;+\;
\underbrace{\frac{e}{10^6}\,P_{CO_2}}_{\text{VND/m CO}_2} w_{co_2},
\qquad
p_{\text{time}} = \frac{c^{h}}{3600}\, w_c
$$

$$
F_{\text{veh}} = f \cdot w_c,
\qquad
Q_{\text{veh}} = \text{capacity} \times \text{DEMAND\_SCALE}
$$

Tổng hàm mục tiêu solver:

$$
Z_{\text{solver}} \;=\; \sum_{k \in K_{\text{used}}} f_k w_c
\;+\; \sum_{k}\sum_{(i,j) \in R_k} \left[ p_{\text{dist}}^{(k)} d_{ij} + p_{\text{time}}^{(k)} t_{ij} \right]
$$

Ký hiệu: $c^{km}$ = `cost_per_km`, $c^{h}$ = `cost_per_hour`, $f$ = `fixed_cost`,
$e$ = `emission_factor` (g/km), $P_{CO_2}$ = `CARBON_PRICE_PER_KG` = 100.000 VND/kg,
$w_c + w_{co_2} = 1$ sau `normalizeWeights`, mặc định $(0{,}7;\ 0{,}3)$.

**Chú ý:** $t_{ij}$ chỉ là **thời gian di chuyển giữa hai điểm**. Thời gian phục vụ và thời
gian chờ time window **không** nằm trong $Z_{\text{solver}}$ — `perWaitingTimeUnit` của
`VehicleTypeImpl` không được set nên mặc định bằng 0.

---

## 3. Tầng báo cáo — công thức được ghi vào DB

Nguồn: `SolutionMetricsCalculator.calculate` (dòng 28–174).

$$
Z_{\text{report}} \;=\;
\underbrace{\sum_{k} f_k}_{\text{fixed}}
\;+\; \underbrace{\sum_{k} D_k \, c^{km}_k}_{\text{fuel}}
\;+\; \underbrace{\sum_{k} H_k \, c^{h}_k}_{\text{time}}
$$

với $D_k$ = quãng đường route $k$ (km) và — điểm mấu chốt —

$$
H_k = \frac{\text{end}_k.\text{arrTime} - \text{start}_k.\text{endTime}}{3600}
$$

tức **toàn bộ thời lượng route**: di chuyển **+ phục vụ + chờ time window**.

CO₂ được tính riêng, **không** cộng vào $Z_{\text{report}}$:

$$
\text{CO}_{2} = \sum_k \frac{D_k \cdot e_k}{1000} \ \text{(kg)},
\qquad
\text{co2CostVnd} = \text{CO}_2 \cdot P_{CO_2}
$$

---

## 4. Lệch #2 — định nghĩa thời gian (lớn hơn lệch CO₂)

Solver trả giá cho $t_{ij}$ (di chuyển). Báo cáo trả giá cho $H_k$ (toàn bộ thời lượng).

Với dataset này, khác biệt **không nhỏ**. Vận tốc trung bình đo được ở job #21 là
**3,3 km/h** — trong khi vận tốc chạy xe thực tế trong nội thành khoảng 25–30 km/h. Suy ra
thời gian di chuyển chỉ chiếm khoảng $3{,}3/30 \approx 11\%$ thời lượng route; phần còn lại
là phục vụ và chờ.

Nghĩa là số hạng thời gian trong $Z_{\text{report}}$ **lớn hơn khoảng 9 lần** số hạng thời
gian mà solver tối ưu. Solver gần như không có động lực giảm thời gian chờ, dù đó là thành
phần chi phối thời lượng thực.

Đây là lệch **lớn hơn** lệch CO₂, và tôi đã bỏ sót nó khi nêu vấn đề lần đầu.

### Kiểm chứng bằng job #21

Giả thiết đội xe chủ yếu là `Truck 5T` ($f = 100.000$, $c^{km} = 8.000$, $c^{h} = 5.000$):

| Thành phần | Tính | Giá trị |
|---|---|---|
| fixed | $958 \times 100.000$ | 95,8 M |
| fuel | $18.058 \times 8.000$ | 144,5 M |
| **cộng lại** | | **240,3 M** |
| cost đo được | | **264,7 M** |
| ⇒ time cost suy ra | $264{,}7 - 240{,}3$ | 24,4 M |
| ⇒ tổng giờ suy ra | $24{,}4\text{M} / 5.000$ | ≈ 4.880 h |
| tổng giờ từ 3,3 km/h | $18.058 / 3{,}3$ | ≈ 5.472 h |

Hai con số cuối lệch ~11% — phù hợp trong phạm vi sai số do đội xe pha trộn nhiều loại. Điều
này **xác nhận** cách đọc công thức $Z_{\text{report}}$ ở mục 3, và xác nhận $H_k$ là thời
lượng toàn phần chứ không phải thời gian di chuyển.

---

## 5. Lệch #1 — mô hình "green" không tạo ra đánh đổi

Dữ liệu thật (`vehicle_types.vehicle_features`), cả **16/16** loại xe:

```json
{"skills": [], "electric": false, "emission_factor": 12.3, ...}
```

Cùng một hệ số, và `electric` đều `false`. Hệ quả toán học:

$$
\text{CO}_2 = \sum_k \frac{D_k \cdot e}{1000} = \frac{e}{1000}\sum_k D_k = \frac{e}{1000} D_{\text{total}}
$$

CO₂ là **hằng số nhân với tổng quãng đường**. Nên "tối thiểu hoá CO₂" và "tối thiểu hoá
quãng đường" là **cùng một bài toán**. Không có mặt Pareto giữa cost và CO₂ — chỉ có một
điểm, xét trên trục đã chuẩn hoá.

### Vậy `ParetoWeightSampler` đang quét cái gì?

Nó vẫn cho ra các nghiệm khác nhau, nhưng không phải vì lý do được ghi trong tài liệu. Nhìn
lại mục 2: khi $w_c \to 0$ thì

$$
p_{\text{dist}} \to \frac{e}{10^6} P_{CO_2} \;(>0),
\qquad
p_{\text{time}} \to 0,
\qquad
F_{\text{veh}} \to 0
$$

Chi phí thời gian và chi phí cố định **triệt tiêu**, chỉ còn chi phí theo quãng đường. Bài
toán suy biến thành "tối thiểu tổng quãng đường với xe miễn phí không giới hạn" — nên solver
dùng rất nhiều xe, mỗi xe chạy một chặng ngắn.

Vậy mặt Pareto thực tế là giữa **tổng quãng đường** và **(chi phí thời gian + chi phí cố
định)**. Đó là một đánh đổi thật và đáng nghiên cứu — nhưng **không phải** đánh đổi
cost–CO₂ như đang được gọi tên.

### Điều này gợi ra hướng phát triển đúng

Chữ "Green" chỉ có nội dung khi **các loại xe khác nhau về cường độ phát thải**. Code đã hỗ
trợ sẵn: `vehicle_features` có cờ `electric` và trường `emission_factor` riêng cho từng loại.
Chỉ là dữ liệu chưa dùng đến.

Đội xe pha trộn diesel + điện làm CO₂ **không còn** tuyến tính với quãng đường, vì lúc đó
$e_k$ phụ thuộc $k$:

$$
\text{CO}_2 = \sum_k \frac{D_k \cdot e_k}{1000}
$$

Khi đó xuất hiện đánh đổi thật: xe điện phát thải thấp nhưng `max_distance` ngắn hơn, và bài
toán trở thành GVRP theo đúng nghĩa trong văn liệu. Đây là việc **đáng làm nhất** để mô hình
xứng với tên gọi.

---

## 6. Lệch #3 và #4 — hai sai đơn vị

### `emission_factor = 12.3` gần như chắc chắn là lít/100 km

12,3 g CO₂/km là bất khả thi với xe tải. Đối chiếu:

| Nguồn | Giá trị |
|---|---|
| Xe tải rigid diesel (Climatiq, EU) | hàng trăm g/km |
| Xe con hiện đại | ~120 g/km |
| **Giá trị trong DB** | **12,3 g/km** |

Nhưng nếu đọc 12,3 là **lít/100 km** — một mức tiêu thụ hợp lý cho xe tải nhẹ — và dùng hệ
số phát thải diesel **2,68 kg CO₂/lít**:

$$
\frac{12{,}3\ \text{L}}{100\ \text{km}} \times 2{,}68\ \frac{\text{kg}}{\text{L}}
= 0{,}330\ \frac{\text{kg}}{\text{km}} = 330\ \frac{\text{g}}{\text{km}}
$$

Con số này hợp lý. Vậy sai số hiện tại là **~27×** theo hướng đánh giá thấp.

Nếu đúng vậy thì cần quyết: đổi dữ liệu sang g/km, hay giữ L/100 km và thêm bước chuyển đổi
tường minh trong code (kèm tên trường phản ánh đơn vị, ví dụ `fuel_consumption_l_per_100km`).
**Đừng để tên trường nói một đơn vị và giá trị mang đơn vị khác** — đó là chính xác loại bẫy
mà `max_duration` đã trả giá.

### Giá carbon cao hơn thị trường khoảng 46×

`CARBON_PRICE_PER_KG = 100.000` VND/kg = 100 triệu VND/tấn.

Giá EU ETS tháng 7/2026 dao động khoảng **79–82 EUR/tấn**. Ở tỉ giá ~27.000 VND/EUR thì
tương đương **~2.160 VND/kg**. Tức giá trong code cao hơn khoảng **46×**.

Javadoc trong `GreenVRPCostCalculator` (dòng 71–76) còn dùng ví dụ 10.000 VND/kg — lệch 10×
so với chính hằng số trong code. Tài liệu đã lạc hậu.

### Hai sai số này che lấp nhau

Với `Truck 5T` ($c^{km} = 8.000$ VND/km):

| Kịch bản | $e$ | $P_{CO_2}$ | CO₂ (VND/m) | fuel (VND/m) | Tỉ trọng CO₂ trong $p_{\text{dist}}$ ở $w=(0{,}7;0{,}3)$ |
|---|---|---|---|---|---|
| Hiện tại | 12,3 g/km | 100.000 | 1,23 | 8,0 | 6,2% |
| Sửa đơn vị $e$ | 330 g/km | 100.000 | 33,0 | 8,0 | **64%** |
| Sửa cả hai | 330 g/km | 2.160 | 0,71 | 8,0 | **3,7%** |

Đọc bảng này kỹ, vì nó dẫn tới một kết luận không hiển nhiên:

**Ở giá carbon thực tế, số hạng CO₂ gần như không thêm thông tin gì.** Lý do: chi phí nhiên
liệu vốn đã tỉ lệ với quãng đường, và CO₂ cũng tỉ lệ với quãng đường — nên khi mọi xe cùng
hệ số phát thải, CO₂ chỉ là nhiên liệu được nhân lại một lần nữa với tỉ lệ nhỏ hơn nhiều.

Nói cách khác: hai sai số đang **bù nhau**. Giá carbon cao 46× kéo tỉ trọng CO₂ lên, hệ số
phát thải thấp 27× kéo xuống, và kết quả ra 6,2% — trông "hợp lý" một cách tình cờ. Sửa một
cái mà không sửa cái kia sẽ làm kết quả lệch mạnh theo hướng tương ứng.

---

## 7. Hệ quả: job #21 không tái lập được

Số đã đo: **18.058 km**, **3.250 kg CO₂**.

$$
\frac{3.250\ \text{kg}}{18.058\ \text{km}} = 0{,}180\ \frac{\text{kg}}{\text{km}} = 180\ \frac{\text{g}}{\text{km}}
$$

Với dữ liệu hiện tại ($e = 12{,}3$) thì cùng quãng đường chỉ cho:

$$
18.058 \times \frac{12{,}3}{1000} = 222\ \text{kg}
$$

Lệch **~14,6×**. Giá trị 180 g/km nằm gần mức fallback 200 g/km trong
`buildGreenVehicleType`, nên suy luận hợp lý nhất là: **lúc job #21 chạy, engine nhận
`emissionFactor = null`** và dùng mặc định 200. (Đây là suy luận từ bất đẳng thức và một
điểm dữ liệu, không phải kết luận chắc chắn — sai lệch 10% còn lại có thể do đội xe pha trộn
hoặc quãng đường dùng để tính CO₂ khác chút.)

Liên quan tới lệch #6: `VehicleFeaturesService.getEmissionFactor` trả **`0.0`** khi thiếu
features, không phải `null`. Nếu Entry gửi `0.0` thì guard `!= null` của engine không bắt
được, `co2CostPerMeter = 0`, và **toàn bộ phần "green" tắt lặng lẽ**. Đây là fail-silent —
trái nguyên tắc "fail-loud hơn sai âm thầm" trong CLAUDE.md.

**Kết luận cho benchmark:** không dùng job #21 làm neo đối chứng cho chỉ số CO₂. Dùng nó để
đối chiếu **quãng đường, số xe, thời gian chạy, và cost** — bốn chỉ số này không phụ thuộc
hệ số phát thải.

---

## 8. Đề xuất chuẩn hoá

### 8.1. Chốt một định nghĩa cost duy nhất

Hợp nhất theo hướng **báo cáo là chuẩn, solver xấp xỉ báo cáo**:

$$
Z^{*} = \sum_{k} f_k + \sum_k D_k c^{km}_k + \sum_k H_k c^{h}_k + \lambda \sum_k \frac{D_k e_k}{1000} P_{CO_2}
$$

với $\lambda \in \{0, 1\}$ là cờ có nội hoá CO₂ hay không, khai báo tường minh trong mỗi
lần chạy. Lý do chọn hướng này: $Z_{\text{report}}$ là con số có nghĩa với người dùng, còn
$Z_{\text{solver}}$ chỉ là phương tiện.

Không thể làm solver khớp $Z^{*}$ hoàn hảo — Jsprit không cho tính phí thời gian chờ qua
`costPerTransportTime`. Nhưng **có** thể thu hẹp khoảng cách bằng
`VehicleTypeImpl.setCostPerWaitingTime()`, hiện đang để mặc định 0. Đây là thay đổi một dòng
và đáng thử: nó cho solver động lực giảm thời gian chờ — thành phần chi phối thời lượng route.

### 8.2. Bỏ trọng số khỏi fixed cost và time cost

Hiện $F = f \cdot w_c$ và $p_{\text{time}} \propto w_c$. Nên khi $w_c \to 0$, xe trở thành
miễn phí và thời gian vô giá trị — bài toán suy biến (mục 5). Đây là hành vi không mong muốn
của một tham số đáng lẽ chỉ điều chỉnh đánh đổi cost–CO₂.

Đề xuất: giữ $f$ và $p_{\text{time}}$ **không nhân trọng số**, chỉ đưa trọng số vào riêng
số hạng CO₂:

$$
p_{\text{dist}} = \frac{c^{km}}{1000} + \mu \cdot \frac{e}{10^6} P_{CO_2}
$$

với $\mu \ge 0$ là **giá carbon bội** (carbon price multiplier) thay cho cặp
$(w_c, w_{co_2})$. Quét $\mu$ từ 0 tới vài chục cho ra một mặt Pareto có ý nghĩa kinh tế
đọc được: "nếu giá carbon là $\mu \times$ giá thị trường thì phương án tối ưu đổi thế nào".
Diễn giải này mạnh hơn nhiều so với "trọng số 0,7/0,3" — vốn không có đơn vị và không ánh xạ
sang bất kỳ đại lượng thực nào.

### 8.3. Sửa dữ liệu phát thải

Ba việc, theo thứ tự:

1. Xác nhận 12,3 là L/100 km hay g/km. Nếu là L/100 km: đổi tên trường thành
   `fuel_consumption_l_per_100km` và chuyển đổi tường minh trong code.
2. Đổi `getEmissionFactor` trả `null` (hoặc ném exception) thay vì `0.0` khi thiếu dữ liệu.
   Fail-loud.
3. Đưa `CARBON_PRICE_PER_KG` ra `application.properties` (Bước 4) và ghi giá trị dùng vào
   mỗi dòng kết quả benchmark.

### 8.4. Tạo đội xe không đồng nhất — việc đáng làm nhất

Đặt `emission_factor` khác nhau theo loại xe, và đưa vài loại `electric: true` vào. Chỉ khi
đó bài toán mới là GVRP thật và mặt Pareto mới không suy biến. Đây vừa là hướng phát triển,
vừa là điều kiện để đối chiếu được với văn liệu E-VRPTW (Schneider et al.).

---

## 9. Ảnh hưởng tới schema CSV kết quả

Thêm các cột sau vào `results/schema.csv` để mỗi dòng tự mô tả được hàm mục tiêu đã dùng:

| Cột | Ý nghĩa |
|---|---|
| `objective_variant` | `legacy_weighted` \| `carbon_multiplier` |
| `carbon_price_vnd_per_kg` | giá trị $P_{CO_2}$ thực dùng |
| `carbon_multiplier` | $\mu$ (nếu dùng biến thể mới) |
| `emission_factor_source` | `db` \| `fallback_200` \| `zero` |
| `emission_unit` | `g_per_km` \| `l_per_100km` |
| `cost_waiting_time_enabled` | đã set `setCostPerWaitingTime` chưa |
| `time_cost_basis` | `transport_only` (solver) \| `elapsed` (report) |

Không có các cột này thì hai dòng kết quả từ hai thời điểm khác nhau **không so được** —
đúng loại lỗi mà job #21 đang mắc.

---

## Nguồn tham chiếu ngoài

- Giá EU ETS tháng 7/2026: khoảng 79–82 EUR/tấn — [IndexBox](https://www.indexbox.io/blog/european-carbon-prices-fluctuate-in-july-2026-ahead-of-ets-reform/), [Carbon Credits](https://carboncredits.com/carbon-prices-today/)
- Hệ số phát thải diesel 2,68 kg CO₂/lít và mức phát thải xe tải — [EDF Business](https://business.edf.org/insights/green-freight-math-how-to-calculate-emissions-for-a-truck-move/), [Climatiq](https://www.climatiq.io/data/emission-factor/1deb995a-fa97-41f3-930e-6d8fd99b20e0)
