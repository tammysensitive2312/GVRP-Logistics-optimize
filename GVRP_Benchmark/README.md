# GVRP_Benchmark

Hạ tầng thí nghiệm cho Giai đoạn 3 (đối soát thuật toán). Nằm trong cùng monorepo với
`GVRP_Entry_API` và `GVRP_Engine_API`, nên **một commit hash bao trọn cả code lẫn cấu hình
thí nghiệm** — không có khả năng lệch pha giữa số đo và code sinh ra nó.

## Bố cục

```
GVRP_Benchmark/
├─ datasets/
│  ├─ vn6183/          dữ liệu thật của dự án (export từ MySQL)
│  ├─ solomon/         instance chuẩn VRPTW
│  ├─ homberger/       instance chuẩn quy mô lớn
│  └─ schneider_evrptw/ instance E-VRPTW (sát GVRP nhất)
├─ scripts/            export, verify, fix sequence
├─ configs/            mỗi thí nghiệm một file cấu hình
├─ results/            CSV thô — KHÔNG được gitignore
└─ analysis/           script/notebook vẽ biểu đồ
```

## Nguyên tắc bất di bất dịch

**Một dòng kết quả không có `git_commit` + `dataset_sha256` là một dòng vô giá trị.**
Không tái lập được thì không dùng để kết luận được.

**Tối thiểu 5 seed mỗi cấu hình.** Jsprit dùng ruin & recreate ngẫu nhiên nên kết quả không
tất định. Báo cáo trung bình ± độ lệch chuẩn, không bao giờ báo cáo một lần chạy đơn lẻ.

**`git_dirty` phải được ghi.** Chạy trên working tree bẩn thì commit hash không mô tả đúng
code đã thực thi. Ghi lại còn hơn tự lừa mình.

## Trạng thái

| Hạng mục | Tình trạng |
|---|---|
| Bố cục thư mục | ✅ xong |
| Schema CSV kết quả | ✅ chốt tại `results/schema.csv` |
| Script export dataset thật | ✅ có, **cần bạn chạy** (xem `scripts/`) |
| Dataset vn6183 đã đóng băng | ⬜ chờ chạy export |
| Instance chuẩn học thuật | ⬜ chờ tải + viết adapter |
| Hàm mục tiêu đã chốt | ⬜ **Bước 3 — chặn mọi thí nghiệm phía sau** |
| Tham số đã externalize | ⬜ Bước 4 |
| `TimeTermination` đã sửa | ⬜ Bước 5 |
| Harness sparse vs full | ⬜ Bước 8 |

Chi tiết từng bước: xem checklist hạ tầng thí nghiệm (tài liệu riêng).
