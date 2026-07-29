# Datasets

Hai loại dataset phục vụ hai mục đích khác nhau. Đừng trộn chúng trong cùng một bảng
so sánh.

| Dataset | Mục đích | So được với |
|---|---|---|
| `vn6183/` | chứng minh khả năng mở rộng trên dữ liệu thật | chính hệ thống ở cấu hình khác |
| `solomon/`, `homberger/`, `schneider_evrptw/` | đối chiếu chất lượng nghiệm với văn liệu | BKS và kết quả công bố |

---

## Instance chuẩn học thuật

### Nguồn

| Bộ | Nội dung | Ghi chú |
|---|---|---|
| **Solomon (1987)** | 56 instance VRPTW, $n = 100$ | 6 lớp: C1/C2 (clustered), R1/R2 (random), RC1/RC2 (hỗn hợp) |
| **Gehring & Homberger** | $n = 200 \dots 1000$ | dùng để kiểm tra khả năng mở rộng, có BKS |
| **Schneider, Stenger & Goeke (2014)** | E-VRPTW — có trạm sạc + time window | sát GVRP nhất trong ba bộ |

Tải từ nguồn chính thức, đặt file gốc vào `<bộ>/raw/`, sinh checksum, rồi ghi lại URL và
ngày tải vào `<bộ>/SOURCE.md`. Không sửa file gốc.

### Ba điểm ánh xạ dễ sai — đọc trước khi viết adapter

**1. Khoảng cách Euclid, không phải quãng đường thật.**
Solomon và Homberger định nghĩa cost bằng khoảng cách Euclid. Hệ hiện tại dùng quãng đường
thật từ GraphHopper. Muốn so với BKS thì **phải** chạy ở chế độ Euclid, nếu không con số
không so được — và sai lệch này không nhỏ: hệ số circuity đo được trên dữ liệu thật nằm ở
`build/circuity-reports/`.

Cần một `DistanceProvider` kiểu Euclid cắm vào thay `GraphHoperDistanceProvider`. Interface
đã có sẵn ở `distance_matrix/DistanceProvider.java`, nên đây là việc thêm một implementation
chứ không phải sửa kiến trúc.

**2. Phải triệt tiêu các số hạng cost không có trong bài toán gốc.**
Solomon không có chi phí cố định xe, không có chi phí thời gian, không có CO₂. Hàm mục tiêu
phải rút về đúng tổng quãng đường:

$$
Z = \sum_{(i,j)} d_{ij}
\quad\Longleftarrow\quad
\texttt{fixedCost} = 0,\;\;
\texttt{costPerHour} = 0,\;\;
w_{co_2} = 0,\;\;
\texttt{costPerKm} = 1
$$

Lưu ý `AppConstant.EPSILON = 0.0001` tồn tại để tránh `costWeight = 0` tuyệt đối — kiểm tra
xem nó có ảnh hưởng tới cấu hình này không.

**3. Đơn vị demand.**
`AppConstant.DEMAND_SCALE = 10` — capacity được nhân 10 khi dựng `VehicleTypeImpl`. Adapter
phải nhất quán với quy ước đó, nếu không sức chứa lệch một bậc.

Liên quan: `SolutionMetricsCalculatorTest` ghi nhận `avgLoadUtilization` bị phóng đại ×10
do chính hệ số này. Xác minh đã sửa chưa trước khi báo cáo bất kỳ số utilization nào.

### Tiêu chí "adapter đúng"

Chạy một instance Solomon đã biết BKS (ví dụ C101, $n = 100$) và kiểm:

- Số xe dùng và tổng khoảng cách nằm trong khoảng hợp lý so với BKS công bố
- `unassigned_orders = 0` — Solomon instance luôn có nghiệm khả thi phục vụ hết
- Nếu lệch BKS quá xa, nghi **adapter** trước khi nghi solver: sai đơn vị hoặc còn sót số
  hạng cost là nguyên nhân phổ biến hơn nhiều so với thuật toán kém

---

## Ghi chú về dung lượng

Xem `../.gitignore`. Nếu file dump vượt ~50 MB, chuyển sang Git LFS hoặc chỉ commit
`.sha256` kèm script tái sinh. **Không** commit file hàng trăm MB vào git thường — repo
phình vĩnh viễn và không gỡ ra được nếu không rewrite lịch sử.
