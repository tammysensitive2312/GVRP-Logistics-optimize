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
    public static final int CLUSTER_FIRST_ORDER_THRESHOLD = 5000;

    /**
     * Kích thước cụm mục tiêu S_target trong công thức
     * C = min(ceil(N / S_target), numVehicles).
     * <p>
     * ⚠️ GIÁ TRỊ TẠM THỜI — CHƯA benchmark, không dùng làm căn cứ quyết định
     * production tới khi có số đo thực nghiệm.
     */
    public static final int CLUSTER_TARGET_SIZE = 800;
}
