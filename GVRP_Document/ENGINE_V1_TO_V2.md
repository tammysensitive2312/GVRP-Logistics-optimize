# Engine v1.0 → v2.0 — những cải thiện và lý do đằng sau

> Soạn 2026-07-28. Nguồn số liệu: job #30–#33 (log engine ngày 27–28/07/2026).
>
> **Câu hỏi mà toàn bộ tài liệu này trả lời:** engine v1.0 chạy được 18 000 orders nhưng
> **treo** ở 50 000. Vì sao, và phải đổi gì để nó chạy được trên một máy tính cá nhân?
>
> Kết luận ngắn: nút thắt **không** phải thuật toán giải, cũng **không** phải tốc độ gọi
> bản đồ. Nó là **cách lưu ma trận khoảng cách** — một chi tiết kỹ thuật tưởng như phụ trợ.
> Sửa nó đưa bộ nhớ từ 37.27 GiB xuống 135.8 MB.

---

## Cách đọc tài liệu này

Mỗi mục cải thiện được dán một trong hai nhãn:

| Nhãn | Nghĩa |
|---|---|
| **[ĐÃ TRIỂN KHAI]** | Code đã viết, có test khoá lại — **trong working tree, CHƯA commit** |
| **[ĐỀ XUẤT]** | Chưa làm. Kèm con số kỳ vọng và cách kiểm để biết nó có tác dụng thật hay không |

> ⚠️ **Về trạng thái git.** `HEAD` hiện tại (`1517108`) **vẫn là v1.0**. Toàn bộ phần
> "[ĐÃ TRIỂN KHAI]" nằm trong working tree chưa commit (~58 file). Điều này có hai hệ quả:
> (1) mọi số dòng trích trong tài liệu này ứng với **working tree**, không phải `HEAD`;
> (2) `GVRP_Benchmark/OBJECTIVE_FUNCTION.md` tự ghi cơ sở là chính commit `1517108` đó, nên
> các số dòng của nó ứng với v1.0 và đã lạc hậu ở một số chỗ (ví dụ nó trích
> `MatrixBasedTransportCosts.getTransportCost` "dòng 57–63", trong working tree method đó ở
> 59–67).

Mỗi công thức được trình bày theo ba lớp, để đọc được mà không cần nền tối ưu tổ hợp:

1. **Công thức** — dạng toán.
2. **Ý nghĩa từng ký hiệu** — không có ký hiệu nào bị bỏ trống.
3. **Thay số ngược** — cắm số liệu đã đo vào và cho thấy nó tái tạo được kết quả đã biết.
   Lớp thứ ba là lớp quan trọng nhất: nó cho phép kiểm công thức mà không cần hiểu suy luận.

Tài liệu liên quan, **không lặp lại ở đây**:

- `GVRP_Benchmark/OBJECTIVE_FUNCTION.md` — phân tích hàm mục tiêu và sáu khiếm khuyết
  của mô hình "green". Mục 7 dưới đây chỉ tóm và trỏ sang.
- `CLAUDE.md` (thư mục gốc) — sổ bẫy kỹ thuật và số liệu đã đo.
- `GVRP_Benchmark/RUNBOOK.md` — quy trình chạy một thí nghiệm.

---

## 1. Bảng ký hiệu

Dùng thống nhất trong toàn tài liệu.

| Ký hiệu | Nghĩa | Đơn vị | Giá trị tham chiếu |
|---|---|---|---|
| $N$ | số **order** (điểm giao hàng) của một job | đơn | 10 054 – 18 054 (đã đo); 50 000 (mục tiêu) |
| $D$ | số **depot** (kho xuất phát) | điểm | ~10 |
| $n$ | tổng số **location** trong ma trận, $n = N + D$ | điểm | 50 010 khi $N = 50\,000$ |
| $V$ | số **xe khả dụng** (fleet cap) | xe | 5 000 – 10 000 |
| $R$ | số **route thực sự dùng** trong nghiệm, $R \le V$ | route | 2 890 – 7 280 (đã đo) |
| $C$ | số **cụm** (cluster) sau bước phân cụm | cụm | $\lceil N/S \rceil$ |
| $S$ | **kích thước cụm mục tiêu** (`CLUSTER_TARGET_SIZE`) | đơn/cụm | 150 |
| $S_c$ | kích thước thực của cụm $c$ | đơn | ~150 |
| $H$ | **heap** JVM tối đa (`-Xmx`) | byte | 8–12 GiB (suy ra) |
| $T$ | thời gian pha **search** của jsprit | giây | 1 150 – 3 420 (đã đo) |
| $d_{ij}$ | khoảng cách đường bộ từ điểm $i$ tới $j$ | mét | — |
| $t_{ij}$ | thời gian **di chuyển** từ $i$ tới $j$ | giây | — |
| $H_k$ | **thời lượng toàn phần** của route $k$ (di chuyển + phục vụ + chờ) | giây | — |
| $H_{\max}$ | thời lượng ca tối đa của một xe (`max_duration`) | **giờ** | 10 |
| $Q$ | dung tích xe | kg | 5 000 |
| $e$ | hệ số phát thải | g CO₂/km | **180** (giá trị engine đang dùng — xem 7.2) |
| $P_{CO_2}$ | giá carbon (`CARBON_PRICE_PER_KG`) | VND/kg | 100 000 (**số giả**) |
| $f$ | chi phí cố định mỗi chuyến (`fixed_cost`) | VND | 100 000 |
| $\ell(i)$ | chỉ số **cục bộ** của điểm $i$ trong cụm của nó, $\ell(i) \in [0, S_c)$ | — | — |
| $\alpha$ | tỉ lệ heap được phép dùng cho ma trận | — | 0.6 |

Hai cái bẫy đơn vị phải nhớ, vì cả hai đã từng gây lỗi thật trong dự án:

- **$H_{\max}$ tính bằng GIỜ.** DB từng lưu phút (480/600) trong khi code nhân 3 600 như giờ
  → ràng buộc ca làm bị vô hiệu hoàn toàn. Mọi con số ở mục 5.5 tỉ lệ **nghịch** với
  $H_{\max}$: nếu ai nhập lại 600 (phút) thì sàn 2 055 xe thành 34 xe.
- **$Q$ trong tài liệu này là kg, nhưng dung tích truyền cho jsprit là $Q \times$
  `DEMAND_SCALE` $= Q \times 10$** (`GreenVRPCostCalculator:128`). Nên log sẽ hiện 50 000
  chứ không phải 5 000 — đó không phải lệch 10×.

Một phân biệt phải nắm ngay, vì nó là nguồn của nhiều nhầm lẫn: $t_{ij}$ là **thời gian
chạy xe giữa hai điểm**, còn $H_k$ là **tổng thời lượng một chuyến**, bao gồm cả thời gian
đứng tại điểm giao hàng và thời gian chờ cửa sổ thời gian mở. Solver trả giá cho $t_{ij}$;
báo cáo trả giá cho $H_k$. Hai đại lượng này lệch nhau khoảng **9×** với dữ liệu hiện tại
(chi tiết ở `OBJECTIVE_FUNCTION.md` mục 4).

---

## 2. Tổng quan v1.0 → v2.0

| Trục | v1.0 | v2.0 | Nhãn |
|---|---|---|---|
| Lưu ma trận | `double[n][n]` dày, $O(n^2)$ | block-diagonal theo cụm, $O(N \cdot S)$ | **[ĐÃ TRIỂN KHAI]** |
| Trần quy mô | $n \le 17\,947$ @ heap 8 GiB | ~2.1 triệu order @ heap 8 GiB | **[ĐÃ TRIỂN KHAI]** |
| Vượt trần | Treo im lặng (GC thrash) | Ném exception kèm $n_{\max}$ | **[ĐÃ TRIỂN KHAI]** |
| Vòng lặp dựng ma trận | $O(n^2)$ = 2.5 tỉ vòng @ 50k | $O(N\cdot S)$ = 8.5 triệu vòng | **[ĐÃ TRIỂN KHAI]** |
| Phân cụm | K-means++ với $S = 150$ cố định | Chọn $S$ theo ràng buộc | **[ĐỀ XUẤT]** |
| Số vòng lặp solver | Hằng 2 000 bất kể quy mô | Scale theo $R$ | **[ĐỀ XUẤT]** |
| Luồng solver | 1 thread | 4–8 thread | **[ĐỀ XUẤT]** |
| Gọi bản đồ | Từng cặp một | Theo lô (batch) | **[ĐỀ XUẤT]** |
| Mô hình CO₂ | Tuyến tính với quãng đường | Phụ thuộc tải/tốc độ | **[ĐỀ XUẤT]** |

---

## 3. Trục 1 — Ma trận khoảng cách **[ĐÃ TRIỂN KHAI]**

Đây là cải thiện lớn nhất, và là lý do job 50k từ chỗ không chạy được thành chạy được.

### 3.1. Ba thế hệ lưu trữ

| Thế hệ | Cấu trúc | Bộ nhớ tại $n = 6\,183$ | Kết cục |
|---|---|---|---|
| 1 | `Map<String, Entry>` | ~7 GB | Bỏ — mỗi ô là một object + một chuỗi khoá |
| 2 | `double[n][n]` dày | 611.7 MB | Bỏ — chạy tốt tới 18k, treo ở 50k |
| 3 | `BlockDiagonalCostMatrix` | **16.5 MB** | **Hiện tại** |

> **Cảnh báo về cách đọc cột cuối.** 16.5 MB là **giảm 37×**, không phải 294×. Tỉ số
> tiết kiệm **phụ thuộc quy mô**: nó bằng $n^2/(NS + 2Dn) \approx n/S$, nên càng $n$ lớn
> càng lợi. Ở $n = 6\,183$ được 37×; ở $n = 50\,010$ được 294.7×. Áp một tỉ số đo ở quy mô
> này sang quy mô khác là đúng loại lỗi mà mục 3.2 cảnh báo — và tôi đã mắc nó ở bản nháp
> đầu tiên của chính tài liệu này (chia 611 cho 294 rồi ghi "~2 MB").

### 3.2. Vì sao thế hệ 2 treo — số học bộ nhớ

Ma trận dày lưu **hai** bảng: khoảng cách và thời gian. Mỗi ô là một `double` (8 byte).

$$M_{\text{dày}}(n) = \underbrace{8}_{\text{byte mỗi ô}} \times \underbrace{2}_{\text{số bảng}} \times \underbrace{n^2}_{\text{số ô mỗi bảng}} = 16 n^2 \text{ byte}$$

**Ý nghĩa:** $n^2$ vì ta cần khoảng cách giữa **mọi cặp** điểm — với $n$ điểm thì có $n^2$
cặp có hướng (kể cả $i \to i$). Hệ số 16 gộp cả hai bảng và độ rộng của `double`.

**Thay số ngược** (job #33, đã chạy xong thật): $n = 18\,054 + 10 = 18\,064$

$$16 \times 18\,064^2 = 5.22\times10^9 \text{ byte} = 4.86 \text{ GiB}$$

Job #33 chạy được với 4.86 GiB, nên heap thực phải đủ chứa nó. Còn job 50k:

$$16 \times 50\,010^2 = 4.00\times10^{10} \text{ byte} = \mathbf{37.27 \text{ GiB}}$$

Đó là con số làm job treo. Và vì $M$ tỉ lệ với $n^2$, tăng $n$ lên 2.77× (18k → 50k) làm
bộ nhớ tăng $2.77^2 = 7.7$×.

### 3.3. Trần theo heap — vì sao tăng `-Xmx` không cứu được

Đảo công thức trên để tìm $n$ lớn nhất mà một heap cho trước còn chịu được:

$$n_{\max} = \sqrt{\frac{H \times \alpha}{16}}$$

**Ý nghĩa:** $H$ là heap tối đa, $\alpha = 0.6$ là **tỉ lệ an toàn** — ta không được dùng
hết heap cho ma trận, vì jsprit còn cần chỗ cho $N$ đối tượng đơn hàng, $R$ đối tượng
route, bộ quản lý trạng thái, và các nghiệm đang so sánh. Vượt ngưỡng đó thì full-GC chạy
liên tục thu hồi được rất ít, và job **treo mà không hề có `OutOfMemoryError`** — đúng
triệu chứng đã quan sát.

| `-Xmx` | $n_{\max}$ | Đủ cho 50k? |
|---|---|---|
| 8 GiB | 17 947 | Không |
| 12 GiB | 21 981 | Không |
| 16 GiB | 25 381 | Không |
| 32 GiB | 35 895 | Không |
| 64 GiB | 50 763 | Vừa đủ |

**Suy ngược heap thực của máy đang dùng:** job #33 chạy xong ở $n = 18\,064$, nên

$$H \ge \frac{M_{\text{dày}}(18\,064)}{\alpha} = \frac{4.86}{0.6} = \mathbf{8.10 \text{ GiB}}$$

Đây là **bất đẳng thức**, không phải ước lượng điểm — mọi heap từ 8.10 GiB trở lên đều
thoả, không có gì nói nó đúng bằng 12.

**Mắt xích giả định của suy luận này** (phải nói ra để nó thành thứ cần kiểm, không phải
điểm mù): phép suy ngược chỉ đúng nếu job #33 **thực sự cấp phát ma trận dày**. Điều đó
đúng ở đây vì job #33 chạy trên code v1.0, mà v1.0 **chỉ có** nhánh dày — bố cục
block-diagonal chưa tồn tại. Với các job chạy sau này, phải kiểm bằng dòng log
`[Matrix] Bố cục DÀY` hoặc `Bố cục BLOCK`; nếu là BLOCK thì 4.86 GiB chưa từng được cấp
phát và bất đẳng thức trên không còn cơ sở.

Từ bảng trên: $n = 50\,010$ đòi $n_{\max} \ge 50\,010$, và mức nhỏ nhất trong bảng thoả là
**64 GiB**. Đó không phải cấu hình của một máy cá nhân, nên **không có mức `-Xmx` nào chữa
được** trong thực tế. Phải đổi thuật toán.

**Còn $\alpha = 0.6$ ở đâu ra?** Đây là **lề an toàn được chọn, không phải số đo.** Phần
40% còn lại dành cho $N$ đối tượng đơn hàng, $R$ đối tượng route, bộ quản lý trạng thái và
các nghiệm đang so sánh — nhưng chưa ai đo tổng của chúng. Vì $n_{\max} \propto \sqrt{\alpha}$,
đổi $\alpha$ từ 0.6 xuống 0.5 làm cả bảng dịch 9%. Nên coi cột $n_{\max}$ là **cấp độ lớn
đáng tin, chữ số cuối thì không**.

### 3.4. Bài học trung tâm: prune giảm TÍNH TOÁN, không giảm BỘ NHỚ

v1.0 **đã có** cơ chế prune. `MatrixMask.needed(i, j)` quyết định cặp nào cần gọi
GraphHopper, và nó loại **99.66%** số cặp ở quy mô 50k (con số này là **số học trên công
thức**, không phải quan sát — chưa job 50k nào chạy xong, đó là tiền đề của cả tài liệu).
Nhưng bộ nhớ **không giảm một byte nào**.

Lý do rất đơn giản khi nhìn ra: ô bị prune vẫn được **ghi giá trị sentinel** vào đúng vị
trí của nó trong mảng dày (`DistanceMatrixService`, bản v1.0, dòng 90–91):

```java
} else if (mask != null && !mask.needed(i, j)) {
    dist[i][j] = MatrixMask.PRUNED_METERS;   // vẫn chiếm đủ 8 byte
    time[i][j] = MatrixMask.PRUNED_SECONDS;
```

Mảng `new double[n][n]` đã được cấp phát trọn vẹn **trước khi** vòng lặp bắt đầu. Prune chỉ
tiết kiệm **lời gọi bản đồ**, không tiết kiệm chỗ chứa.

> **Phát biểu tổng quát:** muốn giảm bộ nhớ thì phải đổi **cách lưu**, không phải đổi
> **cách tính**. Đây là bài học chuyển được sang mọi bài toán ma trận thưa khác.

### 3.5. Bố cục block-diagonal

Quan sát mấu chốt: sau bước phân cụm, **route không được đi xuyên cụm**
(`ClusterRouteConstraint`), và cạnh sentinel bị chặn (`NoPrunedEdgeConstraint`). Nghĩa là
các ô "khác cụm" **không bao giờ nằm trên một cạnh hợp lệ**. Chúng chỉ cần *đọc ra* được
sentinel — không cần *lưu*.

Tập ô cần lưu chính là tập mà `needed(i,j)` trả `true`:

$$\text{needed}(i,j) = \underbrace{[\,i \text{ hoặc } j \text{ là depot}\,]}_{\text{depot nối tới mọi nơi}} \;\vee\; \underbrace{[\,i \text{ hoặc } j \text{ chưa gán cụm}\,]}_{\text{an toàn thì cứ tính}} \;\vee\; \underbrace{[\,c(i) = c(j)\,]}_{\text{cùng cụm}}$$

với $c(i)$ là cụm của điểm $i$. Đếm số ô:

$$\text{cells} = \underbrace{\sum_{c=1}^{C} S_c^2}_{\text{khối vuông mỗi cụm}} + \underbrace{2 D n}_{\text{dải depot: 1 hàng + 1 cột mỗi depot}}$$

**Ý nghĩa từng số hạng:**

- $\sum_c S_c^2$ — mỗi cụm cần một ma trận con đầy đủ giữa các thành viên của chính nó.
  Nếu các cụm bằng nhau ($S_c = S$) thì $C = N/S$ và $\sum_c S_c^2 = C \cdot S^2 = N \cdot S$.
  **Đây là chỗ độ phức tạp sụp từ bậc hai xuống bậc một:** $N \cdot S$ tuyến tính theo $N$
  vì $S$ là hằng số.

  *Chia đều luôn rẻ hơn.* $\sum_c S_c^2$ đạt **nhỏ nhất** khi các cụm bằng nhau — đây là
  bất đẳng thức lồi, nhưng trực giác thì thấy ngay bằng một ví dụ: hai cụm 100 và 200 đơn
  tốn $100^2 + 200^2 = 50\,000$ ô, còn 150 và 150 chỉ tốn $2 \times 150^2 = 45\,000$ ô. Hệ
  quả thực dụng: `ClusterMergeService` gộp cụm lại thì **làm tăng** bộ nhớ, và bảng ở 4.2
  cho thấy prune theo công thức đầy đủ luôn thấp hơn xấp xỉ $1 - 1/C$ một chút vì lý do này.
- $2 D n$ — depot phải nối tới mọi điểm, nên mỗi depot cần một **hàng** đầy đủ (depot → mọi
  nơi) và một **cột** đầy đủ (mọi nơi → depot). Hai chiều vì đường đi không đối xứng.
  Số hạng này tuyến tính theo $n$, và nhỏ vì $D \approx 10$.

Bộ nhớ:

$$M_{\text{block}} = 16 \times \text{cells} = 16\left(N S + 2 D n\right)$$

**Thay số ngược** ($N = 50\,000$, $S = 150$, $D = 10$, $n = 50\,010$, $C = 334$):

Phân hoạch chính xác: 334 cụm cho 50 000 đơn nghĩa là 234 cụm × 150 và 100 cụm × 149, nên
$\sum_c S_c^2 = 234 \times 150^2 + 100 \times 149^2 = 7\,485\,100$ (dùng $NS = 7\,500\,000$
thì ra 136.0 MB — chênh 0.15%, đủ nhỏ để bỏ qua nhưng phải nhất quán khi trích).

$$\text{cells} = 7\,485\,100 + 2 \times 10 \times 50\,010 = 8\,485\,300$$
$$M_{\text{block}} = 16 \times 8\,485\,300 = 1.358\times10^8 \text{ byte} = \mathbf{135.8 \text{ MB}}$$

So với 37.27 GiB dày: **giảm 294.7×** (làm tròn 295×; test dùng phép chia nguyên nên assert
294). Số ô rác so với ô thật là **293.7 : 1**.

**Trần mới:**

$$N_{\max} = \frac{\alpha H}{16 S} = \frac{0.6 \times 8\,\text{GiB}}{16 \times 150} \approx 2.1 \text{ triệu order}$$

Nút thắt bộ nhớ chuyển sang đồ thị đối tượng của jsprit, không còn là ma trận.

**Kiểm chứng tuyến tính** — dấu hiệu để phân biệt $O(N)$ với $O(N^2)$: gấp đôi $N$ thì

| | $N = 50\,000$ | $N = 100\,000$ | Hệ số |
|---|---|---|---|
| dày | 37.27 GiB | 149.04 GiB | **×4.000** |
| block | 135.8 MB | 271.9 MB | **×2.003** |

Cả hai ô cột giữa được tính **lại từ đầu** bằng phân hoạch cụm chính xác (667 cụm cho
100 000 đơn), **không** phải nhân 2 giá trị bên trái — nếu nhân 2 thì bảng này chỉ tái
khẳng định giả thuyết nó định kiểm, tức lập luận vòng tròn. Hệ số 2.003 (không phải 2.000
chằn chặn) chính là dấu hiệu nó được tính thật: phần dư 0.003 đến từ số hạng dải depot
$2Dn$, vốn tuyến tính theo $n$ nhưng không tỉ lệ đúng với $N$.

Hệ số 4 là dấu hiệu bậc hai; hệ số 2 là dấu hiệu bậc một. Test
`BlockDiagonalCostMatrixTest.scaleArithmeticFor50kOrders` khoá đúng hai hệ số này.

### 3.6. Chi tiết bố cục và bất biến bắt buộc

`BlockDiagonalCostMatrix` lưu:

- **Mỗi cụm** một mảng **phẳng** `double[S_c × S_c]` — phẳng chứ không phải
  `double[S_c][S_c]`, để tránh $S_c$ object mảng con và giữ tính cục bộ của cache. Ô
  $(i,j)$ nằm ở offset

  $$\text{offset}(i,j) = \ell(i) \cdot S_c + \ell(j), \qquad \ell(i), \ell(j) \in [0, S_c)$$

  **Ý nghĩa:** $\ell(i)$ là vị trí của điểm $i$ **trong cụm của nó** (0, 1, 2, …), khác với
  chỉ số toàn cục $i \in [0, n)$. Đây là cách "dàn phẳng" một ma trận hai chiều thành một
  mảng một chiều: đi hết hàng $\ell(i)$ rồi cộng thêm $\ell(j)$ cột. Offset lớn nhất là
  $S_c^2 - 1$ — **đây chính là chỗ sinh ra bug tràn `int` ở mục 3.7**, vì offset được tính
  bằng `int`.
- **Dải rộng** cho depot và order chưa gán cụm: hàng đầy đủ và cột đầy đủ. Hai nhóm này
  được gộp vì `needed()` xử lý chúng y hệt nhau.

**Bất biến bắt buộc:** tập $\{(i,j) : \text{needed}(i,j)\}$ phải **trùng khớp tuyệt đối**
tập ô mà cấu trúc block có chỗ lưu. Lệch một ô theo hướng nào cũng sai:

- Ô `needed` nhưng không có chỗ lưu → giá trị tính được bị **ném đi im lặng**, sau đó đọc
  ra sentinel và đơn hàng thành không tới được.
- Ô có chỗ lưu nhưng không `needed` → tốn bộ nhớ vô ích (nhẹ hơn, nhưng vẫn là dấu hiệu
  hai lớp đã lệch nhau).

Bất biến này được kiểm **từng ô trong $n^2$** bởi
`BlockDiagonalCostMatrixTest.blockMatchesDenseCellByCell`, dùng ma trận dày làm oracle.

**Một chi tiết dễ sai:** getter cho cặp khác cụm phải **trả sentinel, không throw**. Lý do:
`NoPrunedEdgeConstraint` và `assertNoSentinelEdgeTraversed` hoạt động bằng cách *đọc được*
sentinel rồi chặn. Nếu getter ném exception thì hai lưới an toàn đó chết thay vì làm việc.

### 3.7. Guard fail-loud

Nhánh dày vẫn còn cho job nhỏ và nhánh Pareto, nhưng giờ **chết ngay với số liệu rõ ràng**
thay vì treo 20 phút:

```java
long required = 16L * n * n;
long maxHeap  = Runtime.getRuntime().maxMemory();
if (required > maxHeap * 0.6) {
    throw new IllegalStateException(...
        "Đây là trần thuật toán O(n²), KHÔNG phải rò rỉ — tăng -Xmx không cứu được");
}
```

Nguyên tắc: **thông báo lỗi tốt nêu con số quan sát được, con số hợp lý, và cách suy ra** —
để lần sau đọc log là biết ngay phải làm gì, không phải điều tra lại từ đầu.

Ba guard đã đặt, mỗi cái chặn một lớp lỗi khác nhau:

| Guard | Chặn gì | Con số |
|---|---|---|
| `MatrixMemory.requireDenseFits` | Cấp phát dày vượt heap | $n_{\max} = \sqrt{\alpha H/16}$ |
| Guard tràn `int` trong block | Cụm quá lớn làm offset quấn vòng | $S_c \le 46\,340$ |
| Guard dải rộng | Order sót cụm làm dải phình | $4 \cdot 8 \cdot W \cdot n > 0.25H$ |

Guard thứ hai đáng nói riêng, vì nó là **bug thật do một reviewer độc lập tìm ra** sau khi
code đã viết xong. Offset trong block tính bằng `int`, nên nếu một cụm có $S_c$ phần tử với

$$S_c^2 > \text{Integer.MAX\_VALUE} = 2\,147\,483\,647 \iff S_c > \lfloor\sqrt{2\,147\,483\,647}\rfloor = 46\,340$$

thì offset **quấn vòng** và đọc lệch slot **mà không có lỗi nào phát ra**. Với $S = 150$
hiện tại thì cách trần 309×, nhưng `ClusterMergeService` có thể gộp cụm lại, nên guard là
cần thiết.

---

## 4. Trục 2 — Phân cụm (cluster)

### 4.1. Pipeline hiện tại **[ĐÃ TRIỂN KHAI]**

Ba bước, mỗi bước giải một vấn đề của bước trước:

```
N order  ──►  KMeansClusterer      ──►  ClusterMergeService  ──►  VehicleClusterAssigner
              (K-means++, C cụm)        (gộp cụm quá nhẹ tải)     (chia xe cho từng cụm)
```

**Bước 1 — K-means++.** Chia order thành $C$ cụm địa lý. Hai quyết định thiết kế:

- *Dùng khoảng cách Euclidean phẳng, không dùng Haversine.* K-means chỉ cần **thứ tự tương
  đối** giữa các khoảng cách để gom nhóm, không cần giá trị tuyệt đối. Euclidean nhanh hơn
  nhiều khi phải tính $N \times C \times$ (số vòng lặp) lần.
  *Javadoc của `KMeansClusterer` khẳng định sai số Euclidean so với Haversine "dưới 1%"
  trong phạm vi 10–20 km — nhưng đó là **khẳng định chưa xác minh**, không có test hay phép
  tính nào chống lưng. Đừng trích nó như số đo.*
- *Khởi tạo bằng K-means++ chứ không random.* Vì thuật toán chỉ chạy **một lần** cho mỗi
  job (không có ngân sách để chạy nhiều lần rồi chọn tốt nhất), centroid ban đầu phải được
  chọn có chủ đích để tránh hội tụ vào cực tiểu địa phương tệ — kiểu hai centroid rơi gần
  nhau và cùng chia một khu vực.

Seed cố định `42L` để **tái lập được**: chạy lại cùng một job phải cho cùng kết quả phân
cụm, nếu không thì không debug được.

**Bước 2 — Gộp cụm nhẹ tải.** K-means chia theo **địa lý**, nên có thể sinh ra cụm mà tổng
nhu cầu nhỏ hơn dung tích của xe nhỏ nhất — cụm đó không đáng một chuyến xe. `ClusterMergeService`
gộp nó vào cụm gần nhất. Ngưỡng gộp phụ thuộc **dữ liệu nghiệp vụ** (dung tích nhỏ nhất
trong các loại xe khả dụng), nên nó là tham số truyền vào, không phải hằng số thuật toán.

**Bước 3 — Chia xe cho cụm.** Quota tỉ lệ theo nhu cầu, rồi gán nearest-greedy. Bất biến
bắt buộc: $\sum_c \text{quota}(c) = V$ — **mọi** xe phải được gán. Vi phạm là dấu hiệu sai
số dấu phẩy động tích luỹ, và phải nổ ngay chứ không được âm thầm bỏ sót xe (sẽ gây đơn
hàng không được phục vụ mà không rõ nguyên nhân).

### 4.2. Số cụm và tỉ lệ prune — công thức

$$C = \min\left(\left\lceil \frac{N}{S} \right\rceil,\; V\right)$$

**Ý nghĩa:** ta muốn mỗi cụm khoảng $S$ đơn hàng, nên cần $\lceil N/S \rceil$ cụm. Chặn
trên bởi $V$ vì không thể có nhiều cụm hơn số xe (cụm không có xe nào thì đơn hàng trong đó
không ai phục vụ).

Tỉ lệ ô được prune, khi các cụm xấp xỉ bằng nhau:

$$\text{prune} = 1 - \frac{\sum_c S_c^2 + 2Dn}{n^2} \;\approx\; 1 - \frac{1}{C}$$

**Suy ra xấp xỉ:** nếu $C$ cụm bằng nhau kích thước $N/C$ thì $\sum_c S_c^2 = C(N/C)^2 = N^2/C$.
Chia cho $n^2 \approx N^2$ được $1/C$. Nên **càng nhiều cụm thì càng prune được nhiều** —
điều này quan trọng vì nó ngược với trực giác "cụm to thì gọn hơn".

**Thay số ngược và so xấp xỉ với thực tế:**

| $N$ | $S$ | $C$ | prune (công thức, cụm bằng nhau) | xấp xỉ $1-1/C$ |
|---|---|---|---|---|
| 1 192 | **800** (giá trị cũ) | 2 | 49.3% | 50.0% |
| 1 192 | **150** (hiện tại) | 8 | 86.3% | 87.5% |
| 12 054 | 150 | 81 | 98.6% | 98.8% |
| 50 000 | 150 | 334 | 99.66% | 99.70% |

Cột thứ tư là **giá trị công thức**, không phải số đo — cả bảng tính từ giả định cụm bằng
nhau. Xấp xỉ $1-1/C$ luôn lạc quan hơn công thức đầy đủ vì nó bỏ số hạng dải depot $2Dn$.

⚠️ **Một chỗ chưa hoà giải được:** javadoc của `AppConstant` và `CLAUDE.md` đều ghi $S=800$
cho prune **41%**, còn công thức ở đây cho **49.3%**. Chênh 8 điểm phần trăm. Con số 41% có
lẽ là số **đo thật** từ một log cũ (cụm không bằng nhau thì prune thấp hơn), nhưng tôi
**không tìm được log đó** để xác nhận. Nên: 49.3% là giá trị công thức, 41% là con số chưa
truy được nguồn. Cách phân giải: chạy một job nhỏ với $S=800$ và đọc `pruned=` ở dòng
`[Matrix] XONG`.

**Kết luận không phụ thuộc chỗ chưa hoà giải đó:** $S = 800$ bị bỏ vì ở $N \approx 1\,200$
nó chỉ tạo **2 cụm** — dù prune là 41% hay 49%, đó vẫn là "tốn công phân cụm mà gần như
không lợi gì". Giá trị 150 tạo 8 cụm và prune ~86%.

### 4.3. Đánh đổi khi chọn $S$ — vì sao không chọn $S$ càng nhỏ càng tốt

$S$ nhỏ thì prune nhiều và bộ nhớ ít, nhưng số xe mỗi cụm mỏng đi. Cụm mỏng xe thì đơn hàng
trong cụm có nguy cơ **không được phục vụ** (`UNASSIGNED`), vì xe của cụm khác không được
sang giúp.

Với $N = 50\,000$, $V = 8\,000$:

| $S$ | $C$ | Bộ nhớ block | Xe/cụm | Đơn/cụm | Nhận xét |
|---|---|---|---|---|---|
| 50 | 1 000 | 56.0 MB | 8.0 | 50 | Quá mỏng xe — rủi ro rớt đơn |
| 100 | 500 | 96.0 MB | 16.0 | 100 | |
| **150** | **334** | **135.8 MB** | **24.0** | **149.7** | **Hiện tại** |
| 200 | 250 | 176.0 MB | 32.0 | 200 | |
| 300 | 167 | 255.5 MB | 47.9 | 299 | |
| 500 | 100 | 416.0 MB | 80.0 | 500 | Bộ nhớ tăng 3× so với 150 |

Bộ nhớ tỉ lệ **thuận** với $S$ (vì $M \approx 16NS$), còn số xe mỗi cụm cũng tỉ lệ thuận
với $S$ (vì $V/C = VS/N$). Nên $S$ là một **núm điều chỉnh trực tiếp** giữa "tiết kiệm bộ
nhớ" và "an toàn không rớt đơn".

Ở mức 135.8 MB, bộ nhớ **không còn là ràng buộc**. Nên hướng đúng là chọn $S$ theo tiêu chí
chất lượng nghiệm, không theo tiêu chí bộ nhớ.

### 4.4. **[ĐỀ XUẤT]** Chọn $S$ theo ràng buộc thay vì hằng số

Hiện $S = 150$ là hằng số biên dịch, và javadoc của nó tự ghi *"⚠️ VẪN CẦN VALIDATE"*
(chuỗi "GIÁ TRỊ TẠM THỜI — CHƯA benchmark A/B" thuộc hằng số khác,
`CLUSTER_FIRST_ORDER_THRESHOLD`). Đề xuất chọn $S$ sao cho mỗi cụm có tối thiểu $v_{\min}$ xe:

$$\frac{V}{C} \ge v_{\min} \iff \frac{V S}{N} \ge v_{\min} \iff S \ge \frac{v_{\min} N}{V}$$

**Ý nghĩa:** $v_{\min}$ là số xe tối thiểu một cụm cần để không rớt đơn — đại lượng này
**phải đo**, không đoán. Với $v_{\min} = 20$, $N = 50\,000$, $V = 8\,000$: $S \ge 125$.

**Cách kiểm:** chạy cùng một dataset với $S \in \{100, 150, 200, 300\}$, ghi lại
`unassigned`, `cost`, `wall time`. Tiêu chí chấp nhận: `unassigned = 0` và cost không xấu
hơn quá 1% so với $S$ tốt nhất. Con số $v_{\min}$ rút ra từ chính bảng đó.

**Cảnh báo về ngoại suy:** mọi con số ở mục 4.3 là **số học trên công thức**, chưa phải số
đo. Chưa có phép A/B nào trên $S$ được chạy. Nên chúng là *dự đoán để kiểm*, không phải
*kết quả*.

---

## 5. Trục 3 — Tham số thuật toán **[ĐỀ XUẤT]**

### 5.1. Luật runtime — công thức và vì sao nó có dạng đó

$$T \approx K \cdot N \cdot R, \qquad K = 3.94\times10^{-5} \text{ s}$$

**Ý nghĩa:** $T$ là thời gian pha search, $N$ số đơn hàng, $R$ số route đang mở. $K$ là chi
phí một "đơn hàng × route" — tức chi phí thử chèn một đơn vào một route.

**Vì sao dạng $N \cdot R$:** engine dùng **regret insertion** (`Jsprit.Construction.REGRET_INSERTION`,
`FAST_REGRET = true`). Mỗi lần cần chèn một đơn hàng, thuật toán phải **quét mọi route đang
mở** để tìm vị trí chèn tốt nhất và tính "độ hối tiếc" nếu không chèn ở đó. Nên chi phí mỗi
vòng lặp tỉ lệ với (số đơn cần chèn) × (số route) $= N \cdot R$. Dạng công thức không phải
giả thiết — nó suy ra từ cách thuật toán làm việc.

**Thay số ngược:**

| job | $N$ | $R$ | $T$ dự đoán | $T$ đo được | Lệch |
|---|---|---|---|---|---|
| 30 | 12 054 | 7 280 | 3 457.5 s | 3 419.6 s | +1.11% |
| 31 | 12 054 | 4 813 | 2 285.8 s | 2 292.9 s | −0.31% |
| 32 | 10 054 | 2 890 | 1 144.8 s | 1 150.3 s | −0.48% |

Ba job lệch dưới 1.2%. Độ tán của $K$ giữa ba job là 1.6%.

**Tính lại theo cách thứ hai** (nguyên tắc: một công thức chỉ đáng tin khi kiểm được bằng
hai đường độc lập). Job #30 và #31 có **cùng** $N = 12\,054$, nên nếu $T \propto N \cdot R$
thì tỉ số thời gian phải bằng tỉ số route:

$$\frac{T_{30}}{T_{31}} = \frac{3\,419.6}{2\,292.9} = 1.491 \qquad\text{so với}\qquad \frac{R_{30}}{R_{31}} = \frac{7\,280}{4\,813} = 1.513$$

Lệch 1.4%. Hai đường độc lập đồng ý — dạng $N \cdot R$ được xác nhận.

**Ngoại suy, có dán nhãn.** Job #33 cho $K = 5.40\times10^{-5}$, **cao hơn 37%** và **chưa
giải thích được** (chạy trên tiến trình JVM khác). Nên $K = 3.94\times10^{-5}$ phải được
coi là **cận dưới**, không phải ước lượng điểm. Dải đã đo là $N \in [10\,054,\, 18\,054]$.

Chiếu cho $N = 50\,000$ — đây là **ngoại suy**, không phải số đo. Và vì $K$ là **cận dưới**,
mọi ô dưới đây là **cận dưới**, phải đọc kèm dấu $\ge$. Cột thứ ba dùng $K_{33} = 5.40\times10^{-5}$
để cho thấy dải thực tế rộng đến đâu:

| $R$ | $\ge$ với $K = 3.94\times10^{-5}$ | nếu $K = K_{33} = 5.40\times10^{-5}$ | 8 thread (giả định hiệu suất 50%) |
|---|---|---|---|
| 3 000 | $\ge$ 1.64 h | 2.25 h | 0.41 – 0.56 h |
| 5 000 | $\ge$ 2.74 h | 3.75 h | 0.68 – 0.94 h |
| 8 000 | $\ge$ 4.38 h | 6.00 h | 1.09 – 1.50 h |

Nói cách khác: job 50k với $R = 8\,000$ mất **4.4 đến 6.0 giờ** trên một luồng. Trình bày nó
như "4.38 h" là biến một bất đẳng thức thành ước lượng điểm — đúng cái lỗi mục 8 cảnh báo.

### 5.2. `maxIterations = 2000` là hằng số — và đây là một lỗi

`OptimizationService:610` đặt 2 000 vòng lặp **bất kể quy mô bài toán**. Bằng chứng cho
thấy điều này gây hại là một cặp job có kiểm soát.

**Job #30 và #31 dùng cùng 12 054 đơn hàng.** Job #31 bị siết fleet cap xuống 5 000 xe.
Kết quả:

| | #30 (cap 10 000) | #31 (cap 5 000) | Chênh |
|---|---|---|---|
| Xe dùng | 7 280 | 4 813 | −33.9% |
| Quãng đường | 112 917 km | 78 674 km | −30.3% |
| Cost báo cáo | 1.779e9 | 1.213e9 | −31.8% |

Job #31 **tốt hơn trên cả ba trục** — tức nó **dominate** job #30.

**Vì sao đây là bằng chứng chứ không phải trùng hợp:** fleet của #31 (5 000 xe) là **tập
con** của fleet #30 (10 000 xe). Nên lời giải của #31 là một lời giải **khả thi** trong bài
toán của #30. Nếu solver của #30 làm việc tới cùng, nó **không thể** cho kết quả tệ hơn một
lời giải khả thi khác. Vậy #30 đơn giản là **bị bỏ dở**.

Định lượng khoảng cách bằng hàm mục tiêu mà solver thực sự tối thiểu hoá (weights mặc định
0.7/0.3):

$$Z = 11.0 \cdot d + 0.972 \cdot t + 70\,000 \cdot R$$

với $d$ tính bằng mét, $t$ bằng giây, $R$ số route. (Hệ số 11.0 đứng trên $e = 180$ — xem
7.3.) Nhưng $t$ ở đây là **thời gian di chuyển** $t_{ij}$, mà log chỉ in **thời lượng toàn
phần** $H_k$. Nên ta chỉ chặn được khoảng, không tính được điểm.

Tổng thời lượng cần cho cận trên **không** có trong log dưới dạng tổng, phải suy ra:

$$\sum_k H_k = R \times H_{\max} \times \text{time util}$$

**Thay số** (job #30): $7\,280 \times 10 \times 0.407 = 29\,630$ h, so với 29 632 h in trong
log — khớp trong 0.007%, xác nhận cách đọc `time util`.

| Cách tính số hạng thời gian | $Z_{30}$ | $Z_{31}$ | $(Z_{30}-Z_{31})/Z_{30}$ |
|---|---|---|---|
| Cận dưới ($t = 0$) | 1.7517e9 | 1.2023e9 | 31.36% |
| Cận trên ($t = \sum H_k$) | 1.8554e9 | 1.2742e9 | 31.32% |

**Cách đọc cột cuối:** đó là "phần mà #30 bỏ lại so với chính nó" — tức nếu solver của #30
tìm được lời giải của #31 thì hàm mục tiêu giảm 31%. Nếu hỏi ngược lại "#30 tệ hơn #31 bao
nhiêu phần trăm" thì con số là 45.7% ($Z_{30}/Z_{31} - 1$). Hai cách phát biểu, cùng một sự
thật — nêu ra để không ai trích lẫn.

Hai cận gần nhau (31.32% và 31.36%) nên kết luận vững bất kể $t$ thật là bao nhiêu:
**job #30 bỏ lại khoảng 31% trên bàn**. Nguyên nhân: 2 000 vòng lặp không đủ khi có ~7 000
route đang mở.

### 5.3. Đề xuất: scale số vòng lặp theo $R$

Đại lượng đáng giữ không đổi là **số lượt mỗi route được xem xét**:

$$\text{lượt/route} = \frac{\text{iterations}}{R}$$

**Thay số:**

| job | $R$ | lượt/route @ 2 000 iters |
|---|---|---|
| 31 (kết quả tốt) | 4 813 | 0.416 |
| 30 (bị bỏ dở) | 7 280 | 0.275 |
| chiếu 50k | 8 000 | 0.250 |

Job #30 chỉ được 66% "sự chú ý" mỗi route so với job #31 — khớp với việc nó bị bỏ dở. Để
job 50k với $R = 8\,000$ đạt mức của #31:

$$\text{iterations} = 0.416 \times 8\,000 \approx 3\,325$$

**Cảnh báo:** đây là **giả thiết cần kiểm**, không phải luật đã đo. Nó dựa trên phỏng đoán
rằng "lượt/route" là đại lượng bảo toàn chất lượng — điều đó chưa được chứng minh, và có
thể sai nếu chất lượng còn phụ thuộc mật độ đơn hàng. Cách kiểm: chạy cùng một dataset với
iterations $\in \{2\,000,\, 4\,000,\, 8\,000\}$ và vẽ cost theo iterations. Nếu đường cong
đã bằng ở 2 000 thì giả thiết sai và nút thắt nằm ở chỗ khác.

Lưu ý về chi phí: $T \propto$ iterations, nên tăng iterations lên 3 325 làm thời gian tăng
1.66×. Tăng iterations là đổi **thời gian** lấy **chất lượng** — phải cân với đòn ở 5.4.

### 5.4. `numThreads = 1` — đòn rẻ nhất chưa dùng

`OptimizationService:611` để mặc định **1 luồng**. jsprit hỗ trợ song song hoá pha chèn qua
`Jsprit.Parameter.THREADS`. Toàn bộ hằng số $K = 3.94\times10^{-5}$ được đo ở 1 luồng.

Đây là đòn nên làm **trước** khi tăng iterations, vì nó mua thời gian để chi cho chất lượng.

**Cách kiểm:** chạy lại đúng job #32 (nhỏ nhất, 1 150 s) với `numThreads` $\in \{1, 4, 8\}$.
Kỳ vọng: thời gian giảm, **cost gần như không đổi** (song song hoá không được làm đổi
nghiệm). Nếu cost đổi đáng kể thì có phi tất định trong song song hoá — đó là vấn đề phải
biết.

### 5.5. Fleet cap là đòn kép

$R$ xuất hiện **tuyến tính** trong $T = K N R$, nên siết fleet cap vừa cải thiện chất lượng
(mục 5.2) vừa giảm thời gian. Nhưng phải biết khi nào cap trở thành có hại:

$$\text{cap đang BIND} \iff \frac{R}{V} > 96\%$$

**Thay số:** job #31 dùng 4 813 trên cap 5 000, tức **96.26%** — sát trần. Nghĩa là nghiệm
của #31 **bị ép**, chưa phải tối ưu tự nhiên; hạ cap thêm có thể còn tốt hơn, cho tới khi
đơn hàng bắt đầu rớt. Job #30 dùng 72.80% cap nên cap **không** bind — nó tệ hơn không phải
vì thiếu xe.

**Sàn dưới cho số xe** — hai sàn độc lập, sàn nào cao hơn thì đó là ràng buộc thật:

$$R_{\min}^{\text{tải}} = \frac{\sum_i q_i}{Q}, \qquad R_{\min}^{\text{thời gian}} = \frac{\sum_k H_k}{H_{\max}}$$

với $q_i$ là nhu cầu đơn $i$, $Q$ dung tích xe, $H_{\max} = 10$ **giờ** là thời lượng ca tối
đa. Nhắc lại bẫy đơn vị ở mục 1: $H_{\max}$ phải là **giờ**; nếu DB lại lưu 600 (phút) thì
sàn thời gian tụt từ 2 055 xuống 34 xe, và toàn bộ đề xuất #7 ở mục 9 mất cơ sở.

**Thay số** (job #31): tổng nhu cầu ~1.25 triệu kg, $Q = 5\,000$ kg ⇒
$R_{\min}^{\text{tải}} = 250$ xe. Tổng thời lượng 20 545 h ⇒ $R_{\min}^{\text{thời gian}} = 2\,055$ xe.

$$\frac{R_{\min}^{\text{thời gian}}}{R_{\min}^{\text{tải}}} = \frac{2\,055}{250} = 8.2\times$$

**Ràng buộc chặn là THỜI GIAN, không phải tải.** Đây là kết luận có hệ quả trực tiếp: tối
ưu theo tải (xếp đầy xe) không giúp gì; phải tối ưu theo thời gian. Và nó giải thích vì sao
`Load util = 3.4–12.6%` là **con số đúng** chứ không phải bug — xem mục 8.

Đang dùng 4 813 xe so với sàn thời gian 2 055, nên vẫn còn dư địa. **Đề xuất:** quét cap
$\in \{4\,000, 3\,000, 2\,500\}$ trên đúng dataset job #30/#31, weights cố định.

---

## 6. Trục 4 — Dựng ma trận và gọi bản đồ

### 6.1. Luật prep — tuyến tính **[ĐÃ TRIỂN KHAI]**

"Prep" là toàn bộ phần trước pha search: `prepareContext` + K-means + dựng ma trận.

$$T_{\text{prep}} \approx \kappa \cdot N, \qquad \kappa \approx 81 \text{ ms/order}$$

**Vì sao tuyến tính:** số lời gọi GraphHopper bằng số ô cần lưu $\approx N S + 2Dn$. Vì $S$
là **hằng số**, số hạng chi phối $N S$ tuyến tính theo $N$. Nếu $S$ tăng theo $N$ thì luật
này sẽ thành bậc hai — nên tính tuyến tính là **hệ quả của việc chọn $S$ cố định**, không
phải tính chất tự nhiên.

**Thay số ngược:**

| $N$ | $T_{\text{prep}}$ | ms/order |
|---|---|---|
| 10 054 | 807 s | 80.3 |
| 12 054 | 934 s | 77.5 |
| 12 054 | 1 102 s | 91.4 |
| 18 054 | 1 382 s | 76.5 |

Bốn điểm đo nằm trong dải 76.5–91.4 ms/order, không có xu hướng tăng theo $N$ — đúng là
tuyến tính.

**Chiếu $N = 50\,000$, tính bằng hai đường:**

1. Theo ms/order: $81.43 \times 50\,000 = 4\,072$ s $= 1.13$ h (dùng trung bình 4 job;
   nếu lấy đúng 81.4 thì ra 4 070 s).
2. Theo số ô: job #33 dựng 3 055 076 ô trong 1 382 s ⇒ 452 µs/ô. Job 50k cần 8 485 300 ô
   ⇒ $8\,485\,300 \times 452\,\mu s = 3\,838$ s $= 1.07$ h.

Hai đường lệch 6%.

> ⚠️ **Hai đường này KHÔNG độc lập — đừng dùng sự đồng ý của chúng làm bằng chứng.**
> Bản nháp đầu của tài liệu này gọi chúng là "hai đường độc lập" và coi 6% là xác nhận
> chéo. Sai. Lý do: số ô trên mỗi order gần như là hằng số ($\text{cells}/N$ = 169.2 ở
> $N=18\,054$ và 169.7 ở $N=50\,000$), nên "µs mỗi ô" và "ms mỗi order" chỉ là **cùng một
> phép đo chia theo hai mẫu số tỉ lệ với nhau**. Kiểm: đường 1 áp riêng cho job #33 cho
> $76.5 \times 50\,000 = 3\,825$ s, so với đường 2 là 3 838 s — lệch **0.3%**. Vậy cái "6%"
> chỉ là độ tán ms/order giữa job #33 và trung bình bốn job, không phải sự đồng ý của hai
> phương pháp.
>
> **Muốn xác nhận chéo thật** thì cần một phép đo **khác loại**: ví dụ đọc `ms/location` ở
> dòng `[Matrix] XONG` của một job **chưa dùng** để lập luật, hoặc đo trực tiếp thời gian
> một lời gọi GraphHopper đơn lẻ rồi nhân với số ô.

Điểm còn giữ nguyên giá trị: luật prep là **tuyến tính** (bốn điểm đo, không có xu hướng
tăng theo $N$), và **prep không phải nút thắt** — ~1.1 h so với 4.4–6.0 h của pha search.

Log đã in `ms/location` ở dòng `[Matrix] XONG` để mỗi job tự cập nhật hằng số $\kappa$, thay
vì phải suy ngược từ mốc thời gian.

### 6.2. Vòng lặp dựng ma trận cũng đã hết bậc hai **[ĐÃ TRIỂN KHAI]**

Bản v1.0 duyệt **mọi** cặp $(i,j)$ dù chỉ để ghi sentinel:

```java
for (int j = 0; j < n; j++) {          // luôn n vòng
    if (mask != null && !mask.needed(i, j)) {
        dist[i][j] = PRUNED_METERS;
        pruned.incrementAndGet();       // atomic bị tranh chấp giữa các thread
```

Ở $n = 50\,010$ đó là **2.5 tỉ** vòng lặp, mỗi ô bị prune còn gọi một `AtomicInteger`
increment bị nhiều luồng tranh chấp. Bản v2.0 chỉ duyệt các ô **có chỗ lưu** (8.5 triệu),
qua `matrix.targetsFor(i)`.

Điều đáng chú ý: ở dải 10k–18k, phần này **không** chi phối (luật prep vẫn tuyến tính vì
lời gọi GraphHopper ở 452 µs/ô đắt hơn hẳn một vòng lặp ghi sentinel). Nhưng ở 50k, tỉ lệ
ô rác so với ô thật là **293.7 : 1**, nên nó sẽ nổi lên. Đây là ví dụ về việc **một chi phí
bậc hai vô hại ở quy mô nhỏ trở thành chi phối ở quy mô lớn**.

### 6.3. **[ĐỀ XUẤT]** Gọi GraphHopper theo lô

Hiện `GraphHoperDistanceProvider.fetch(from, to)` gọi **từng cặp một**, ~452 µs/cặp. Repo đã
có `BatchGraphHopperMatrixProvider` nhưng nó **không có annotation Spring** và không được
`@Qualifier` trỏ tới — tức đang chết.

GraphHopper có API matrix nhận nhiều điểm nguồn và đích một lần, chia sẻ được phần tìm đường
chung. Cấu trúc block-diagonal **rất phù hợp**: mỗi cụm là đúng một lô $S_c \times S_c$.

**Con số kỳ vọng:** nếu lô cắt được một nửa chi phí mỗi ô thì prep(50k) từ ~3 840 s xuống
~1 900 s. **Chưa có số đo nào** — hệ số cắt phải đo, không đoán.

**Cách kiểm:** chạy đúng job #32 với provider từng-cặp và provider lô, so `ms/location` ở
dòng `[Matrix] XONG`, và so ma trận kết quả từng ô để chắc chắn giá trị không đổi.

---

## 7. Trục 5 — Hàm mục tiêu **[ĐỀ XUẤT]**

Phân tích đầy đủ ở `GVRP_Benchmark/OBJECTIVE_FUNCTION.md`. Ở đây chỉ nêu bốn điểm ảnh hưởng
trực tiếp tới việc mở rộng quy mô, kèm số liệu từ job #30–#33.

### 7.1. Ba định nghĩa "cost" đang cùng tồn tại

| # | Cái gì | Công thức | Ai dùng |
|---|---|---|---|
| 1 | Cost solver | $11.0\,d + 0.972\,t_{ij} + 70\,000\,R$ | jsprit tối thiểu hoá |
| 2 | Cost báo cáo | $\sum_k \left(c^{km}_k D_k + c^h_k H_k + f_k\right)$ | ghi vào DB, in log |
| 3 | CO₂ cost | $P_{CO_2} \times \text{CO}_2$ | in riêng, **không** cộng vào (2) |

Hàng 2 phải viết theo **từng xe** ($c^{km}_k$, $c^h_k$, $f_k$), không phải hằng số chung.
Bốn job #30–#33 tình cờ đều dùng một loại xe `Truck 5T` nên nó **thu về**
$8\,000\,D_{km} + 5\,000\,H_k + 100\,000\,R$ — nhưng `data.sql` cho thấy đội xe có
`cost_per_km` từ 2 000 đến 8 000 và `fixed_cost` từ 5 000 đến 100 000. Dùng dạng thu gọn cho
đội xe pha trộn sẽ ra sai số lớn.

Ba con số khác nhau cho cùng một nghiệm. Khi benchmark **phải nói rõ đang so cái nào**, nếu
không thì hai lần đo không so được với nhau.

Hai lệch cụ thể: (a) cost báo cáo dùng tham số **thô**, không nhân weights; (b) solver trả
giá cho $t_{ij}$ (di chuyển) còn báo cáo trả giá cho $H_k$ (toàn phần) — lệch ~9×.

### 7.2. CO₂ cộng tuyến hoàn hảo với quãng đường

$$\text{CO}_2 = \sum_k \frac{D_k \cdot e_k}{1000} \;\xrightarrow{\;e_k = e \;\forall k\;}\; \frac{e}{1000} D_{\text{total}}$$

**Thay số:** ở cả bốn job, $\text{CO}_2 / D_{\text{total}} = 0.180000$ kg/km — giống nhau
đến hết số chữ số mà log in ra, dù bốn job khác nhau hoàn toàn về fleet, mật độ và số đơn.
(Tôi tính được các tỉ số này lệch nhau dưới $10^{-15}$, nhưng đó là hệ quả của việc chia hai
số `double` do chính engine sinh ra, **không** phải bằng chứng độc lập — log không in đủ 15
chữ số hữu nghĩa để khẳng định như vậy.)

**Hệ quả toán học:** "tối thiểu CO₂" và "tối thiểu quãng đường" là **cùng một bài toán**.
Mặt Pareto cost↔CO₂ là **một điểm**, không phải đường cong.

Đây là khiếm khuyết **mô hình**, không phải giá trị tham số — thay số không chữa được. Phải
làm $e$ phụ thuộc **loại xe** (đội xe pha trộn diesel + điện), hoặc phụ thuộc **tải/tốc độ**.

*Ghi chú:* giá trị 180 g/km xuất hiện ở mọi loại xe là hardcode
`VehicleFeaturesDTO.defaultFeatures()` (PETROL_CAR) phía Entry — tức JSON `vehicle_features`
đang không tới được engine. Còn `CARBON_PRICE_PER_KG = 100 000` và `emission_factor = 12.3`
đều là **số giả** đặt tạm khi chưa có giá thật; đừng suy luận gì từ giá trị của chúng.

### 7.3. Weights nhân bất đối xứng

`GreenVRPCostCalculator.buildGreenVehicleType` nhân `fixed` và `time` với **chỉ**
$w_c$, nhưng `distance` với **cả hai** $w_c$ và $w_{co2}$. Hệ quả đo được qua **break-even
detour** — đường vòng dài nhất mà solver còn chấp nhận thay vì mở một xe mới:

$$\text{break-even} = \frac{f \cdot w_c}{p_{\text{dist}}}, \qquad p_{\text{dist}} = \underbrace{\frac{c^{km}}{1000} w_c}_{\text{nhiên liệu}} + \underbrace{\frac{e}{10^6} P_{CO_2} w_{co2}}_{\text{CO}_2}$$

**Ý nghĩa:** tử số là chi phí mở xe mới (đã nhân weight); mẫu số là chi phí mỗi mét. Chia
ra được "bao nhiêu mét đường vòng thì đắt bằng một xe mới".

**Thay số cho mẫu số — và đây là chỗ PHẢI khai báo giá trị $e$:**

$$\frac{c^{km}}{1000} = \frac{8\,000}{1\,000} = 8.00 \text{ VND/m}, \qquad \frac{e}{10^6} P_{CO_2} = \frac{180}{10^6} \times 100\,000 = 18.00 \text{ VND/m}$$

$$\Rightarrow p_{\text{dist}}(0.7,\, 0.3) = 8.00 \times 0.7 + 18.00 \times 0.3 = 5.6 + 5.4 = 11.00 \text{ VND/m}$$

> ⚠️ **$e = 180$ g/km, KHÔNG phải 12.3 của DB.** Toàn bộ bảng dưới đây đứng trên $e = 180$,
> vì đó là giá trị **engine thực sự dùng** (kiểm được: log cho CO₂/km $= 0.180$ ở cả bốn
> job). Nếu dùng $e = 12.3$ như DB ghi thì số hạng CO₂ chỉ còn 1.23 VND/m, $p_{\text{dist}}$
> thành 5.97, và **cả bảng đổi khoảng 8×** (ECO_FOCUSED break-even thành 8.1 m thay vì
> 0.56 m; biến động 1 540× thay vì 22 500×).
>
> `GVRP_Benchmark/OBJECTIVE_FUNCTION.md` mục 6 tính với $e = 12.3$ nên **ra số khác tài liệu
> này**. Không tài liệu nào sai công thức — chúng khai $e$ khác nhau. Tài liệu đó soạn trước
> khi phát hiện engine đang dùng 180, và nó phỏng đoán engine dùng default 200; phỏng đoán
> đó **đã bị bác bỏ**: 180 khớp *chính xác*
> `VehicleFeaturesDTO.defaultFeatures()` (PETROL_CAR), không phải "gần 200".
>
> Khi trích bất kỳ con số nào ở mục này, **phải nói rõ đang dùng $e$ nào**.

| Preset | $p_{\text{dist}}$ (VND/m) | $f \cdot w_c$ | break-even |
|---|---|---|---|
| COST_FOCUSED (1, 0) | 8.00 | 100 000 | 12 500 m |
| mặc định (0.7, 0.3) | 11.00 | 70 000 | 6 364 m |
| BALANCED (0.5, 0.5) | 13.00 | 50 000 | 3 846 m |
| ECO_FOCUSED (1e−4, 1) | 18.00 | 10 | **0.56 m** |

Biến động **22 500×**. Ở ECO_FOCUSED, solver mở một xe mới để tiết kiệm **nửa mét** — nghiệm
suy biến thành một đơn/xe. Đây là lỗi cấu trúc, không phải lựa chọn tham số.

### 7.4. `fixed_cost = 100 000` VND/chuyến quá rẻ

Chia cho giá mỗi km: $100\,000 / 8\,000 = 12.5$ km. Mở một xe tải 5 tấn chỉ "đắt" bằng 12.5
km chạy xe.

**So sánh cho đúng loại đại lượng.** 12.5 km là ngưỡng *đường vòng thêm*, còn "2.65–9.37 km
mỗi đơn" là *quãng đường trung bình trên mỗi đơn hàng* — hai thứ khác nhau, không so trực
tiếp được. Cách so đúng là lấy **tổng quãng đường một chuyến**: job #31 có
$78\,674 / 4\,813 = 16.3$ km mỗi xe. Vậy chi phí cố định 100 000 VND chỉ bằng
$12.5 / 16.3 = 77\%$ chi phí quãng đường của chính chuyến đó. Một xe "gần như miễn phí" so
với việc chạy nó — nên **phân mảnh đội xe một phần là nghiệm kinh tế ĐÚNG của mô hình**,
không phải lỗi solver.

Thực tế một chuyến xe 5T (tài xế + khấu hao + nhiên liệu cố định) nên cỡ 500 k – 1 M VND.
Sửa tham số này sẽ làm số xe giảm mạnh, và đó là thay đổi **mô hình**, phải ghi rõ khi
benchmark.

---

## 8. Trục 6 — Khả kiểm chứng **[ĐÃ TRIỂN KHAI]**

Một cải thiện không nằm trong hiệu năng nhưng quan trọng không kém: làm cho mọi phát biểu
số học **kiểm được mà không cần hiểu suy luận**.

### 8.1. Vì sao cần

Trong quá trình phân tích, công thức runtime từng được viết là $T \approx 3.94\times10^{-8} N R$
— **sai 1000×**. Bảng số trong cùng tài liệu thì đúng; chỉ phần chữ sai số mũ. Phép kiểm
bắt được nó chỉ mất mười giây:

$$3.94\times10^{-8} \times 12\,054 \times 7\,280 = 3.5 \text{ s} \quad\text{so với } 3\,419.6 \text{ s đo được}$$

Lệch 989×. **Bài học:** một tài liệu có thể tự mâu thuẫn trong chính nó, và chỉ phép thay số
ngược bắt được.

### 8.2. Chỉ số dễ đọc sai — tính giá trị đúng trước khi gọi là bug

**`Load util` là tải, không phải thời gian.** Nó bằng $(2.04 \text{–} 2.08)\% \times$
(đơn/xe) ở cả bốn job — tức **hoàn toàn xác định bởi mật độ đơn**, không mang thông tin gì
về chất lượng nghiệm. (Bản nháp viết "bằng đúng 2.05%"; thực tế job #31 cho 2.076% nên
"bằng đúng" là nói quá — hệ số khít nhất là ~2.06%.) Kết hợp với mục 5.5 (ràng buộc chặn là thời gian, 8.2× so với tải), kết luận:
load util thấp là **đúng**, và báo cáo nó như KPI chính là **sai chiều**.

**Vận tốc trung bình 2.90–3.83 km/h** — chậm hơn người đi bộ. Suy luận định tính: phần lớn
thời lượng route là phục vụ + chờ cửa sổ thời gian, không phải chạy xe. **Chưa lượng hoá
được** tỉ lệ chính xác vì log không in service time, nên không trích một con số phần trăm
cho điều này.

**Thời gian job in ra bị sai.** `OptimizationService:101` dùng `Duration.toMinutesPart()`,
hàm này trả phút **trong giờ** (0–59), nên số giờ bị ăn mất. Job #33 chạy 1h10m52.9s được
in là `10m 52s`. Ngoài ra `took X seconds` của jsprit **chỉ** tính pha search — phần prep
chiếm 21–41% wall time mà không có dòng log nào.

### 8.3. Nơi các phát biểu được khoá lại

| Cơ chế | Khoá gì |
|---|---|
| `MeasuredLawsTest` | Mọi con số trong `CLAUDE.md`: tham số suy ngược, luật runtime, luật prep, bảng trần bộ nhớ, bảng break-even, dominance #30/#31 |
| `BlockDiagonalCostMatrixTest` | Bất biến bố cục ma trận, so từng ô trong $n^2$ |
| Guard trong code | Nếu công thức sai thì guard nổ sai lúc và lộ ra |
| Reviewer độc lập | Một agent soát code mà **không** thấy suy luận của người viết. Đã bắt được bug tràn `int` ở mục 3.7, và bảy lỗi trong bản nháp tài liệu này |

**Những bảng trong tài liệu này CHƯA có test khoá** — nói ra để không ai tưởng mọi con số ở
đây đều được canh:

| Mục | Bảng/con số | Trạng thái |
|---|---|---|
| 4.2 | Tỉ lệ prune theo $C$ | Đã tính lại độc lập, chưa có assertion |
| 4.3 | Đánh đổi $S$ (bộ nhớ / xe mỗi cụm) | Số học công thức, **chưa A/B** |
| 5.2 | Hai cận $Z$ (31.36% / 31.32%) | Test chỉ assert `gap > 25%` và chỉ dùng cận trên |
| 5.3 | Lượt/route và 3 325 iterations | Giả thiết, chưa kiểm |
| 6.1 | Đường tính prep thứ hai | Đã được xác định là **không độc lập** |
| 6.2 | 2.5 tỉ vs 8.5 triệu vòng lặp | Số học công thức |

Tôi đã tính lại tất cả bằng một đường độc lập và chúng đều đúng ở thời điểm viết — nhưng
"đúng hôm nay" khác với "có cơ chế bắt hồi quy".

Bốn luật đã thành giao ước trong `CLAUDE.md`: (1) mọi công thức kèm một dòng thay số ngược;
(2) trích source phải có `file:dòng`; (3) ngoại suy phải dán nhãn kèm dải đã đo; (4) việc
lớn phải đứng trên một con số đã kiểm.

---

## 9. Bảng tổng hợp đề xuất

Mỗi hàng: đổi gì → kỳ vọng bao nhiêu → **kiểm thế nào**. Cột cuối là cột quan trọng nhất;
đề xuất không có cách kiểm thì không nên làm.

| # | Đề xuất | Con số kỳ vọng | Cách kiểm | Ưu tiên |
|---|---|---|---|---|
| 1 | `numThreads` 1 → 8 | 4–8× nhanh pha search | Chạy lại job #32, cost phải gần như không đổi | **Cao** — rẻ nhất |
| 2 | `Map<String,Location>` thay quét tuyến tính tìm depot | Bỏ 800 triệu vòng `String.equals` @ 50k | Đo `prepareContext` trước/sau | **Cao** |
| 3 | Hoist `buildGreenVehicleType` ra khỏi vòng lặp xe | 2 object thay vì 8 000 | Đếm object, đo prep | **Cao** |
| 4 | `maxIterations` scale theo $R$ | 0.416 lượt/route ⇒ 3 325 iters @ $R{=}8000$ — **GIẢ THIẾT chưa kiểm**, xem 5.3 | Vẽ cost theo iters $\in \{2k,4k,8k\}$; nếu đường cong đã bằng ở 2k thì giả thiết sai | Trung bình |
| 5 | Nối `BatchGraphHopperMatrixProvider` | Prep 3 840 s → ~1 900 s (**chưa đo**) | So `ms/location`, so ma trận từng ô | Trung bình |
| 6 | Chọn $S$ theo $v_{\min}$ thay vì hằng 150 | $S \ge v_{\min}N/V$ | A/B $S \in \{100,150,200,300\}$, tiêu chí unassigned = 0, gap cost ≤ 1% | Trung bình |
| 7 | Quét fleet cap xuống 2 500 | Sàn thời gian là 2 055 xe | Cap $\in \{4k, 3k, 2.5k\}$, cùng dataset và weights | Trung bình |
| 8 | Sửa `toMinutesPart()` + log tách prep/search | Đọc đúng thời gian job | Đối chiếu với dòng `Starting optimization` | **Cao** — rẻ |
| 9 | CO₂ phụ thuộc tải hoặc loại xe | Pareto front thành đường cong thật | Kiểm $\text{CO}_2/D$ **khác nhau** giữa các job | Cao cho luận văn |
| 10 | `fixed_cost` 100 k → 500 k–1 M | Break-even 12.5 km → 62.5 km | Số xe phải giảm mạnh; ghi rõ là đổi mô hình | Cao cho luận văn |

Thứ tự đề nghị: **1 → 8 → 2 → 3** (rẻ, không đổi nghiệm) rồi mới **4 → 7 → 6 → 5** (đổi
nghiệm, cần benchmark), cuối cùng **9 → 10** (đổi mô hình, thuộc phạm vi luận văn).

Nguyên tắc: **mỗi bản vá một commit riêng**, gắn với **một** giả thuyết đã được số học xác
nhận. Sửa gộp thì mất khả năng biết cái nào có tác dụng.

---

## 10. Còn thiếu để kết luận vững

Ba lỗ hổng trong bằng chứng hiện tại, nêu ra để không ai tưởng bức tranh đã đầy:

1. **Chưa có điểm đo ở 25 000 orders.** Hằng số $K$ nhảy 37% ở job #33 mà chưa giải thích
   được. Nếu nó tiếp tục trôi lên thì chiếu 50k đang lạc quan. Đây là phép đo **đáng làm
   nhất** hiện giờ.
2. **Chưa đối chiếu block vs dense trên NGHIỆM THẬT.** Đã có test so từng ô ở tầng ma trận,
   nhưng chưa chạy cùng một job qua hai bố cục rồi so nghiệm. Tiêu chí: không rớt đơn, gap
   cost ≤ 1%.
3. **Chưa A/B trên $S$.** Mọi con số ở mục 4.3 là số học trên công thức, chưa phải số đo.

---

## 11. Nguồn tham chiếu

**Trong repo:**

- `CLAUDE.md` — sổ bẫy kỹ thuật, số liệu đã đo, giao ước kiểm chứng
- `GVRP_Benchmark/OBJECTIVE_FUNCTION.md` — phân tích hàm mục tiêu, sáu khiếm khuyết
- `GVRP_Benchmark/RUNBOOK.md` — quy trình chạy thí nghiệm
- `GVRP_Engine_API/src/test/.../MeasuredLawsTest.java` — assertion cho mọi con số ở đây
- `GVRP_Engine_API/src/test/.../BlockDiagonalCostMatrixTest.java` — bất biến bố cục ma trận

**Code được trích trong tài liệu này:**

| Nội dung | Vị trí |
|---|---|
| Bố cục block-diagonal | `distance_matrix/BlockDiagonalCostMatrix.java` |
| Số học bộ nhớ và guard | `distance_matrix/MatrixMemory.java` |
| Hai nhánh dựng ma trận | `distance_matrix/DistanceMatrixService.java` |
| Quy tắc prune | `distance_matrix/MatrixMask.java:needed` |
| K-means++ | `clustering/KMeansClusterer.java` |
| Gộp cụm nhẹ tải | `clustering/ClusterMergeService.java` |
| Chia xe cho cụm | `clustering/VehicleClusterAssigner.java` |
| `maxIterations`, `numThreads` | `service/OptimizationService.java:610-611` |
| Bug định dạng thời gian | `service/OptimizationService.java:101-104` |
| Weights và CO₂ (ba dòng gánh kết luận: 123, 126, 127) | `service/GreenVRPCostCalculator.java:88-138` |
| `S = 150`, giá carbon | `utils/AppConstant.java` |

**Thư viện ngoài** (chữ ký đã tra từ source thật, không đoán từ trí nhớ):

- `VehicleTypeImpl.VehicleCostParams` — field `public final fix`, `perDistanceUnit`,
  `perTransportTimeUnit`; `VehicleTypeImpl` **không** có `getFixedCost()`.
  Nguồn: `graphhopper/jsprit`, `jsprit-core/.../vehicle/VehicleTypeImpl.java`
- `Jsprit.Builder.addConstraints` mặc định `true` — core constraints (time window, load,
  skills) **vẫn** được áp qua `PrettyAlgorithmBuilder.addCoreStateAndConstraintStuff()` kể
  cả khi truyền `ConstraintManager` riêng. Nên nếu một ràng buộc có vẻ bị bỏ qua, **nghi dữ
  liệu trước khi nghi solver**.
