package org.truong.gvrp_engine_api.utils;

public class AppConstant {
    // 1 solver unit = 0.1 kg (or 0.1 pallet)
    public static final int DEMAND_SCALE = 10;

    /**
     * Carbon pricing (VND per kg CO2)
     * Can be adjusted based on:
     * - Government carbon tax
     * - Company ESG policy
     * - International carbon market price
     */

    public static final double CARBON_PRICE_PER_KG = 100000.0;

    /**
     * Epsilon weight để tránh costWeight=0.0 tuyệt đối.
     * Lý do: khi costWeight=0, cost matrix triệt tiêu hoàn toàn tín hiệu
     * chi phí nhiên liệu, khiến Jsprit không phân biệt được các route
     * có CO2 bằng nhau nhưng khác biệt lớn về quãng đường/chi phí vận hành.
     * Giá trị đồng bộ với GVRP_Entry_API.util.AppConstant.EPSILON.
     */
    public static final double EPSILON = 0.0001;

    /**
     * Ngưỡng số order để BẬT cluster-first pre-processing (Fisher-Jaikumar
     * style, xem VehicleClusterAssigner + ClusterRouteConstraint).
     * Dưới ngưỡng này, Jsprit solve trực tiếp trên toàn bộ bài toán — chi phí
     * phân cụm (K-means + merge + assign) không bù lại được lợi ích giảm
     * search space.
     * <p>
     * ⚠️ GIÁ TRỊ TẠM THỜI — CHƯA benchmark A/B. Cần đo elapsed time + cost +
     * unassigned có/không cluster-first ở các mốc 5k/10k/20k orders (style
     * JspritConvergenceBenchmarkTest) trước khi coi là ngưỡng chính thức.
     */
    public static final int CLUSTER_FIRST_ORDER_THRESHOLD = 1000;

    /**
     -     * Kích thước cụm mục tiêu S_target trong công thức
     -     * C = min(ceil(N / S_target), numVehicles).
     -     * <p>
     -     * ⚠️ GIÁ TRỊ TẠM THỜI — CHƯA benchmark, không dùng làm căn cứ quyết định
     -     * production tới khi có số đo thực nghiệm.
     +     * Kích thước cụm mục tiêu S_target trong công thức
     +     * C = min(ceil(N / S_target), numVehicles).
     +     * <p>
     +     * CÁCH CHỌN: tỉ lệ prune của Tầng 0 ≈ 1 − 1/C. Muốn cắt nhiều cặp thì cần
     +     * NHIỀU cụm (C lớn), tức S_target NHỎ:
     +     *   - S_target = 800 → với N=1192 chỉ ra C=2 → prune ~41% (gần như vô ích).
     +     *   - S_target = 150 → C=8 → prune ~87%; S_target=100 → C=12 → prune ~92%.
     +     * Chọn 150 làm điểm khởi đầu: cắt mạnh (41%→~87%) nhưng vẫn giữ ~numVehicles/C
     +     * xe mỗi cụm (vd 199/8 ≈ 25 xe/cụm) để không làm mỏng đội xe gây rớt đơn.
     +     * <p>
     +     * AN TOÀN KHI HẠ THẤP: ClusterMergeService là lưới chặn — nếu chia quá mịn
     +     * khiến cụm có tổng demand < capacity xe nhỏ nhất, nó TỰ gộp lại. Nên hạ
     +     * S_target không gây cụm "quá nhỏ về tải"; giới hạn thực tế của C là
     +     * min(ceil(N/S_target), numVehicles, ~tổngDemand/minCapacity).
     +     * <p>
     +     * ⚠️ VẪN CẦN VALIDATE: đo prune% / unassigned / build time / cost (harness
     +     * mục 5 tài liệu) trước khi chốt production; chỉnh trong khoảng 100–200 tùy
     +     * số đo unassigned trên dữ liệu thật.
     */
    public static final int CLUSTER_TARGET_SIZE = 150;

}
