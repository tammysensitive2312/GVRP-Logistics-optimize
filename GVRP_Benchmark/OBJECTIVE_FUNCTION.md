# Hàm mục tiêu — phân tích và trạng thái chuẩn hoá

> Soạn lần đầu 2026-07-27 trên commit `1517108`. **Cập nhật 2026-08-25 trên commit
> `38425a8`** sau đợt vá `452d0c5` (vehicle_features + api_key + task executor) và các sửa
> đơn vị CO₂ ở Engine.
>
> Kết luận ngắn — **hai tầng, đọc kỹ vì chúng khác nhau**:
> 1. Về *cơ chế*: bug "mọi xe cùng emission_factor bị nuốt thành fallback" đã được **sửa
>    tận gốc** — `vehicle_features` được tái thiết kế, `parseFeatures` giờ fail-loud, đơn
>    vị CO₂ đã đúng. Xem mục 0.
> 2. Về *mô hình*: **"green" vẫn chưa tạo ra đánh đổi thật**, vì dữ liệu vẫn để mọi loại xe
>    cùng một hệ số phát thải và Entry chưa gửi hệ số theo từng loại xuống Engine đúng
>    nghĩa. Đây vẫn là việc đáng làm nhất. Xem mục 5 và 8.4.

---

## 0. Những gì đã thay đổi kể từ bản 1517108 — đọc trước

Bản tài liệu gốc mô tả code ở commit `1517108`. Bốn điều dưới đây đã khác. Phần thân dưới
vẫn giữ phân tích lịch sử (có giá trị để hiểu *vì sao* các quyết định được đưa ra), nhưng
mỗi chỗ lỗi thời đều được đánh dấu **[ĐÃ SỬA]**.

| # | Bản 1517108 | Hiện tại (`38425a8`) | Vị trí |
|---|---|---|---|
| A | `VehicleFeaturesDTO` có getter `isElectric()` / `getSkills()` chỉ-đọc → JSON không round-trip được → `emission_factor` thật bị nuốt, engine luôn nhận **180** (fallback PETROL_CAR) | DTO tái thiết kế: chỉ còn `category` (enum `VehicleCategory`), `emissionFactor` (`@NotNull @DecimalMin("0.0")`), `skills` (`Set<VehicleSkill>` — **field thật**, round-trip được). Không còn getter-giả-làm-field | `dto/request/VehicleFeaturesDTO.java` |
| B | `getEmissionFactor` trả `0.0` khi thiếu dữ liệu (fail-silent) | `parseFeatures` **ném `DataInvalidException`** khi JSON rỗng/hỏng; `getEmissionFactor` trả thẳng giá trị (có thể null, nhưng `@NotNull` ở DTO chặn từ input) — **fail-loud** | `service/VehicleFeaturesService.java` |
| C | CO₂: `emission g/km ÷ 1e6 × CARBON_PRICE_PER_KG(=100000 VND/kg)`; guard fallback 200 khi null | `emission g/km × CARBON_PRICE_PER_TON(=150000 VND/tấn) ÷ 1e9`; **không còn** guard fallback trong `buildGreenVehicleType` (unbox thẳng) | `GreenVRPCostCalculator.java:107-109`, `utils/AppConstant.java:14` |
| D | Trọng số mặc định `(0.7, 0.3)` | Trọng số mặc định `(0.5, 0.5)` | `mapper/OptimizationConfigMapper.java` |

Ba lệch **chưa** sửa, vẫn đúng như phân tích gốc: bất đối xứng trọng số (mục 8.2), CO₂ tuyến
tính hoàn toàn với quãng đường khi đội xe đồng nhất (mục 5), và số hạng thời gian solver ≠
báo cáo (mục 4 — nhưng `setCostPerWaitingTime` đã được thêm, xem mục 4).

---

## 1. Sáu phát hiện gốc, kèm trạng thái hiện tại

| # | Phát hiện | Mức độ | Trạng thái |
|---|---|---|---|
| 1 | Cả 16 loại xe có `emission_factor` **giống hệt nhau** → CO₂ tuyến tính với quãng đường → "đa mục tiêu" là ảo, Pareto suy biến một điểm | Nghiêm trọng | **Còn** — là vấn đề *dữ liệu + mô hình*, không phải bug code. Xem mục 5, 8.4 |
| 2 | Solver và báo cáo dùng hai định nghĩa THỜI GIAN khác nhau (di chuyển vs toàn bộ thời lượng), lệch ~9× | Nghiêm trọng | **Giảm nhẹ** — `setCostPerWaitingTime` đã set (mục 4); lệch vẫn còn vì service time không nằm trong cost solver |
| 3 | `emission_factor = 12.3` gần như chắc chắn là **lít/100 km** bị dùng như **g/km**, lệch ~27× | Cao | **Còn** — quyết định đơn vị vẫn treo. Xem mục 6 |
| 4 | Giá carbon quá cao so với thị trường | Cao | **Đổi** — giờ là 150.000 VND/**tấn** = 150 VND/kg, *thấp hơn* EU ETS ~14×. Xem mục 6 |
| 5 | Báo cáo không cộng CO₂ trong khi solver có tối ưu | Trung bình | **Còn** — `SolutionMetricsCalculator` vẫn tính `co2CostVnd` riêng, không cộng vào `totalCost` |
| 6 | `getEmissionFactor` trả `0.0` khi thiếu (fail-silent) | Trung bình | **[ĐÃ SỬA]** — nay fail-loud (mục 0.B) |

---

## 2. Tầng solver — công thức Jsprit thực sự tối ưu (HIỆN TẠI)

Nguồn: `GreenVRPCostCalculator.buildGreenVehicleType` và `MatrixBasedTransportCosts`.

Adapter tái tạo ngữ nghĩa `VehicleRoutingTransportCostsMatrix`:

$$
c(i,j,\text{veh}) = p_{\text{dist}} \cdot d_{ij} + p_{\text{time}} \cdot t_{ij}
$$

với $d_{ij}$ = **mét**, $t_{ij}$ = **giây**. Tham số theo vehicle type:

$$
p_{\text{dist}} = \underbrace{\frac{c^{km}}{1000}}_{\text{VND/m nhiên liệu}} w_c
\;+\;
\underbrace{\frac{e \cdot P_{CO_2}}{10^{9}}}_{\text{VND/m CO}_2} w_{co_2},
\qquad
p_{\text{time}} = \frac{c^{h}}{3600}\, w_c
$$

$$
F_{\text{veh}} = f \cdot w_c,
\qquad
Q_{\text{veh}} = \text{capacity} \times \text{DEMAND\_SCALE}
$$

**Đổi so với bản cũ:** số hạng CO₂. Trước là $\frac{e}{10^6}P^{kg}_{CO_2}$ với
$P^{kg}=100\,000$ VND/kg. Nay là $\frac{e \cdot P^{tấn}_{CO_2}}{10^9}$ với
$P^{tấn}=150\,000$ VND/tấn. Kiểm đơn vị:
$[\text{g/km}]\times[\text{VND/tấn}] = \frac{g}{km}\cdot\frac{VND}{10^6 g}
= \frac{VND}{10^6 km} = \frac{VND}{10^9 m}$ — đúng thứ nguyên.

Thay số ($e=12{,}3$): $12{,}3 \times 150\,000 / 10^9 = 0{,}001845$ VND/m. Kiểm chéo đường
thứ hai: $12{,}3\text{ g/km} = 1{,}23\times10^{-5}$ tấn/m; $\times 150\,000 = 0{,}0018\overline{45}$
VND/m ✓.

Tổng hàm mục tiêu solver:

$$
Z_{\text{solver}} = \sum_{k \in K_{\text{used}}} f_k w_c
+ \sum_{k}\sum_{(i,j) \in R_k}\!\left[ p_{\text{dist}}^{(k)} d_{ij} + p_{\text{time}}^{(k)} t_{ij} \right]
$$

Ký hiệu: $c^{km}$=`cost_per_km`, $c^{h}$=`cost_per_hour`, $f$=`fixed_cost`,
$e$=`emission_factor` (g/km), $P_{CO_2}$=`CARBON_PRICE_PER_TON`=150.000 VND/tấn,
$w_c + w_{co_2}=1$ sau `normalizeWeights`, **mặc định nay $(0{,}5;\ 0{,}5)$**.

**[CẬP NHẬT]** `perWaitingTimeUnit` **đã được set** = `timeCostPerSecond × w_c` (bằng
`perTransportTime`) — `buildGreenVehicleType` gọi `.setCostPerWaitingTime(weightedTimeCost)`.
Bản cũ để mặc định 0. Tuy nhiên **service time** vẫn không vào $Z_{\text{solver}}$, nên lệch
ở mục 4 chỉ giảm chứ chưa mất.

---

## 3. Tầng báo cáo — công thức ghi vào DB (HIỆN TẠI)

Nguồn: `SolutionMetricsCalculator.calculate`.

$$
Z_{\text{report}} = \sum_{k} f_k + \sum_{k} D_k\,c^{km}_k + \sum_{k} H_k\,c^{h}_k
$$

với $D_k$ = quãng đường route $k$ (km), $H_k = \frac{\text{arrTime}_{\text{end}} -
\text{endTime}_{\text{start}}}{3600}$ = **toàn bộ thời lượng route** (di chuyển + phục vụ +
chờ time window).

CO₂ tính riêng, **không** cộng vào $Z_{\text{report}}$:

$$
\text{CO}_2 = \sum_k \frac{D_k \cdot e_k}{1000}\ \text{(kg)},
\qquad
\text{co2CostVnd} = \frac{\text{CO}_2}{1000}\cdot P^{tấn}_{CO_2}
$$

**[CẬP NHẬT]** dòng `co2CostVnd`: trước là `totalCO2 × P^{kg}`; nay là
`totalCO2 / 1000 × getCarbonPrice()` — vì `getCarbonPrice()` giờ trả giá theo **tấn**, phải
chia CO₂(kg) cho 1000 thành tấn trước khi nhân. Đơn vị khớp: $\text{tấn}\times\text{VND/tấn}
= \text{VND}$ ✓.

⚠️ Lưu ý một điểm chưa đồng bộ giữa hai tầng: `buildGreenVehicleType` (solver) **không còn**
fallback khi `emissionFactor` null (unbox thẳng → NPE nếu null lọt xuống), nhưng
`calculateRouteCO2` (báo cáo) **vẫn còn** fallback `200.0`. Đường đi bình thường có `@NotNull`
ở DTO chặn null từ input nên không ai nổ; nhưng hai tầng đang xử lý null khác nhau — nên
thống nhất (xem mục 8.3).

---

## 4. Lệch #2 — định nghĩa thời gian

Solver trả giá cho $t_{ij}$ (di chuyển) + nay cả **waiting time** (đã set
`setCostPerWaitingTime`). Báo cáo trả giá cho $H_k$ (toàn bộ thời lượng, gồm cả **service
time**). Service time vẫn chỉ có ở báo cáo.

Với dataset này khác biệt không nhỏ: vận tốc trung bình job #21 là **3,3 km/h** so với
25–30 km/h chạy thực → di chuyển chỉ ~11% thời lượng route. Việc thêm `setCostPerWaitingTime`
cho solver **một phần** động lực giảm chờ; phần service time thì vẫn nằm ngoài tầm với của
solver. Nên lệch giảm nhưng chưa triệt tiêu.

### Kiểm chứng bằng job #21 (vẫn hợp lệ)

Đội xe chủ yếu `Truck 5T` ($f=100.000$, $c^{km}=8.000$, $c^{h}=5.000$):

| Thành phần | Tính | Giá trị |
|---|---|---|
| fixed | $958 \times 100.000$ | 95,8 M |
| fuel | $18.058 \times 8.000$ | 144,5 M |
| cộng | | 240,3 M |
| cost đo được | | 264,7 M |
| ⇒ time cost suy ra | $264{,}7-240{,}3$ | 24,4 M |
| ⇒ tổng giờ suy ra | $24{,}4\text{M}/5.000$ | ≈ 4.880 h |
| tổng giờ từ 3,3 km/h | $18.058/3{,}3$ | ≈ 5.472 h |

Hai số cuối lệch ~11% — phù hợp trong sai số do đội xe pha trộn. Xác nhận $H_k$ là thời
lượng toàn phần.

---

## 5. Lệch #1 — mô hình "green" vẫn chưa tạo đánh đổi

Dữ liệu thật hiện tại (`vehicle_types.vehicle_features`) vẫn để **16/16 loại xe cùng một hệ
số phát thải** (12,3). Hệ quả toán học không đổi:

$$
\text{CO}_2 = \sum_k \frac{D_k \cdot e}{1000} = \frac{e}{1000}\,D_{\text{total}}
$$

CO₂ là **hằng số nhân tổng quãng đường** → "tối thiểu CO₂" ≡ "tối thiểu quãng đường". Không
có mặt Pareto cost–CO₂, chỉ một điểm.

### Break-even detour ở tham số MỚI (e=12,3, P=150k/tấn, mặc định 0,5/0,5)

Break-even để mở xe mới $= \dfrac{f\cdot w_c}{p_{\text{dist}}}$. `Truck 5T`:

| preset | $w_c$ | $w_{co_2}$ | $p_{\text{dist}}$ (VND/m) | break-even | tỉ trọng CO₂ trong $p_{\text{dist}}$ |
|---|---|---|---|---|---|
| COST_FOCUSED | 1,0 | 0,0 | 8,0000 | 12.500 m | 0% |
| **mặc định / BALANCED** | 0,5 | 0,5 | 4,0009 | **12.497 m** | **0,023%** |
| ECO (EPSILON,1) | 1e-4 | 1,0 | 0,0026 | 3.781 m | 69,8% |

Đọc kỹ dòng mặc định: **CO₂ chỉ đóng góp 0,023%** vào chi phí quãng đường. Ở giá carbon và
hệ số hiện tại, trục CO₂ gần như **không ảnh hưởng định tuyến** — hợp lý cho giai đoạn dev
khi cả hai còn là số giả, nhưng nghĩa là mọi nghiệm hiện giờ thực chất chỉ do trục cost.

Để so, chế độ **cũ** (e=180, P=100.000 VND/kg, mặc định 0,7/0,3): $p_{\text{dist}}=11{,}0$,
break-even 6.364 m, CO₂ chiếm ~6,2%. Việc chuyển 180→12,3 và đổi trọng số kéo tỉ trọng CO₂
từ 6,2% xuống 0,023% — trục xanh gần như biến mất khỏi bài toán.

### Điều này gợi hướng phát triển đúng (không đổi so với bản cũ)

Chữ "Green" chỉ có nội dung khi **các loại xe khác cường độ phát thải**. Mô hình dữ liệu MỚI
đã sẵn sàng cho việc này hơn hẳn: `VehicleFeaturesDTO` giờ có `emissionFactor` riêng mỗi loại
và `skills: Set<VehicleSkill>` với `ELECTRIC`/`HYBRID`/`PETROL`/`DIESEL`. Khi $e_k$ phụ thuộc
$k$, CO₂ không còn tuyến tính với quãng đường và đánh đổi thật xuất hiện. Nhưng **hai việc còn
thiếu**: (a) đặt dữ liệu $e_k$ khác nhau theo loại xe; (b) đưa `skills` xuống Engine — hiện
`EngineVehicleTypeDTO` chỉ mang `emissionFactor`, skills chưa tới Jsprit (Engine vẫn gán cứng
`addSkill("STANDARD")`).

---

## 6. Lệch #3 và #4 — hai sai đơn vị (một đã đổi hướng)

### `emission_factor = 12.3` — quyết định đơn vị VẪN treo

12,3 g CO₂/km là bất khả với xe tải. Nếu đọc là **lít/100 km** với hệ số diesel 2,68
kg/lít: $\frac{12{,}3}{100}\times 2{,}68 = 0{,}330$ kg/km = **330 g/km** — hợp lý. Sai số
hiện tại ~27× theo hướng đánh giá thấp. **Chưa quyết** đổi dữ liệu sang g/km hay giữ L/100km
+ chuyển đổi tường minh (kèm đổi tên trường). Đây vẫn là quyết định phải làm.

### Giá carbon — **[ĐỔI]** giờ THẤP hơn thị trường

Bản cũ 100.000 VND/kg = 100 triệu/tấn, cao ~46× EU ETS. Nay `CARBON_PRICE_PER_TON = 150.000`
VND/tấn = **150 VND/kg**. EU ETS 7/2026 ~79–82 EUR/tấn ≈ ~2.160 VND/kg, tức giá mới **thấp
hơn ~14×**. Kết hợp với việc bỏ hệ số 180→12,3, số hạng CO₂ nay gần như triệt tiêu (0,023%,
mục 5) — ngược hẳn tình huống cũ nơi hai sai số bù nhau ra "trông hợp lý" 6,2%.

Cả `CARBON_PRICE_PER_TON` và `emission_factor` vẫn là **số giả do người dùng đặt tạm** —
đừng suy luận gì từ trị số tuyệt đối; chỉ dùng chúng để kiểm công thức và đơn vị.

---

## 7. Neo đối chứng job #21 — CO₂ vẫn không dùng làm neo

Số đã đo: 18.058 km, 3.250 kg CO₂ → $3.250/18.058 = 0{,}180$ kg/km = **180 g/km**. Đây là
dấu vết của **bug fallback cũ** (engine nhận null → dùng 180 PETROL_CAR). Với dữ liệu hiện
tại ($e=12{,}3$) cùng quãng đường chỉ cho 222 kg — lệch ~14,6×.

**[GHI CHÚ CẬP NHẬT]** Bug sinh ra con số 180 nay đã sửa (mục 0). Nghĩa là job chạy *sau*
commit `452d0c5` sẽ cho CO₂ khác hẳn job #21 — nên **không** so CO₂ giữa hai thời kỳ. Bốn
chỉ số neo vẫn hợp lệ vì không phụ thuộc hệ số phát thải:

| Chỉ số | Giá trị | Neo được? |
|---|---|---|
| Xe dùng | 958 | ✅ |
| Quãng đường | 18.058 km | ✅ |
| Cost | 264.655.468 VND | ✅ |
| Load util | 13,9% | ✅ |
| Thời gian | 13m40s | ⚠️ chỉ khi cùng số luồng |
| CO₂ | 3.250 kg | ❌ (bug fallback cũ) |

---

## 8. Trạng thái chuẩn hoá

### 8.1. Chốt một định nghĩa cost — MỘT PHẦN đã làm

Hướng: báo cáo là chuẩn, solver xấp xỉ. `setCostPerWaitingTime` đã thêm (mục 2, 4) — đúng đề
xuất gốc. **Còn thiếu:** service time vẫn ngoài $Z_{\text{solver}}$; và chưa có cờ $\lambda$
tường minh để chọn có nội hoá CO₂ vào tổng cost hay không.

### 8.2. Bỏ trọng số khỏi fixed cost và time cost — CHƯA làm

Vẫn $F = f\cdot w_c$ và $p_{\text{time}}\propto w_c$ (`buildGreenVehicleType:126-128`). Nên
khi $w_c\to 0$ (ECO), xe thành gần miễn phí và thời gian gần vô giá trị → nghiệm suy biến
(bảng mục 5 cho ECO: $p_{\text{dist}}=0{,}0026$, fixed=10, break-even 3.781 m — vẫn méo).

Đề xuất giữ nguyên: tách trọng số ra khỏi $f$ và $p_{\text{time}}$, chỉ đưa vào số hạng CO₂
bằng một **giá carbon bội** $\mu \ge 0$ thay cặp $(w_c, w_{co_2})$:

$$
p_{\text{dist}} = \frac{c^{km}}{1000} + \mu\cdot\frac{e\,P_{CO_2}}{10^{9}}
$$

Quét $\mu$ cho ra mặt Pareto đọc được theo ngôn ngữ kinh tế ("nếu giá carbon gấp $\mu$ lần
thị trường thì phương án tối ưu đổi thế nào").

### 8.3. Dữ liệu phát thải — MỘT PHẦN đã làm

1. **[CHƯA]** Xác nhận 12,3 là L/100km hay g/km; đổi tên trường nếu là L/100km.
2. **[ĐÃ SỬA]** `getEmissionFactor` không còn trả 0.0 — nay fail-loud. Nhưng còn một chỗ
   chưa đồng bộ: `calculateRouteCO2` (báo cáo) vẫn fallback 200 trong khi
   `buildGreenVehicleType` (solver) unbox thẳng. Nên thống nhất: hoặc cả hai fail-loud, hoặc
   cả hai cùng một fallback tường minh.
3. **[CHƯA]** Đưa `CARBON_PRICE_PER_TON` ra `application.properties` và ghi giá trị dùng vào
   mỗi dòng kết quả benchmark. Hiện vẫn hằng số trong `utils/AppConstant.java`.

### 8.4. Đội xe không đồng nhất — việc đáng làm nhất, VẪN chưa làm

Đặt `emissionFactor` khác nhau theo loại và đưa vài loại `ELECTRIC` vào `skills`. Chỉ khi đó
Pareto mới không suy biến và bài toán mới là GVRP thật, đối chiếu được với văn liệu E-VRPTW
(Schneider et al.). Mô hình dữ liệu MỚI đã dọn đường; còn thiếu (a) dữ liệu $e_k$ đa dạng,
(b) đường đưa `skills` từ Entry → `EngineVehicleTypeDTO` → ràng buộc `addSkill`/
`addRequiredSkill` trong Jsprit (hiện gán cứng "STANDARD").

---

## 9. Ảnh hưởng tới schema CSV kết quả

Vẫn nên thêm các cột để mỗi dòng tự mô tả hàm mục tiêu đã dùng — **cập nhật đơn vị**:

| Cột | Ý nghĩa |
|---|---|
| `objective_variant` | `legacy_weighted` \| `carbon_multiplier` |
| `carbon_price_vnd_per_ton` | giá trị $P_{CO_2}$ thực dùng (**đơn vị tấn**, không phải kg) |
| `carbon_multiplier` | $\mu$ (nếu dùng biến thể 8.2) |
| `emission_factor_source` | `db` \| `fallback_200` \| `null_error` |
| `emission_unit` | `g_per_km` \| `l_per_100km` |
| `cost_waiting_time_enabled` | đã set `setCostPerWaitingTime` chưa (**nay = true**) |
| `time_cost_basis` | `transport_plus_wait` (solver) \| `elapsed` (report) |

---

## Nguồn tham chiếu ngoài

- Giá EU ETS 7/2026 ~79–82 EUR/tấn — IndexBox, Carbon Credits.
- Hệ số phát thải diesel 2,68 kg CO₂/lít — EDF Business, Climatiq.
