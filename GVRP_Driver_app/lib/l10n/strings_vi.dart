/// All user-facing strings (DA-R11). Texts marked [§5]/[§6] in
/// `work-items/DRIVER-APP-001/ui-design.md` §5 match `design.md` exactly.
abstract final class S {
  static const appTitle = 'GVRP Tài xế';
  static const loginSubtitle = 'Xem và đi theo tuyến được giao';
  static const configError =
      'Ứng dụng chưa được cấu hình địa chỉ máy chủ. Liên hệ quản trị viên.';

  // Login
  static const loginTitle = 'Đăng nhập';
  static const branchName = 'Chi nhánh';
  static const username = 'Tên đăng nhập';
  static const password = 'Mật khẩu';
  static const loginButton = 'Đăng nhập';
  static const showPassword = 'Hiện mật khẩu';
  static const hidePassword = 'Ẩn mật khẩu';
  static const notDriver = 'Tài khoản này không phải tài xế.';
  static const badCredentials = 'Sai chi nhánh, tên đăng nhập hoặc mật khẩu.';
  static const sessionExpired =
      'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.';
  static const logout = 'Đăng xuất';

  // My routes
  static const myRoutes = 'Tuyến của tôi';
  static String sentAt(String formatted) => 'Gửi lúc $formatted';
  static String sender(String name) => 'Người gửi: $name';
  static const supersededTag = 'Phiên bản cũ';
  static const newerVersionBanner =
      'Có phiên bản tuyến mới hơn — tuyến hiện hành nằm ở đầu danh sách';
  static const noRoutes = 'Chưa có tuyến nào được giao.';

  // Route detail
  static const supersededBanner =
      'Đây là phiên bản cũ — không đi theo tuyến này';
  static const zoneBanner = 'Tuyến có vùng hạn chế — đi đúng đường trên bản đồ';
  static const guidanceTitle = 'Hướng dẫn';
  static String stepCounter(int i, int n) => 'Bước $i/$n';
  static const allSteps = 'Xem tất cả bước';
  static const noGuidance = 'Chưa có hướng dẫn từ máy chủ cho chặng này.';
  static const nextStop = 'Điểm tiếp theo';
  static const nextLabel = 'Tiếp theo';
  static const orderBadge = 'Đơn hàng';
  static const routeFinished = 'Đã đến điểm cuối của tuyến.';
  static const arrived = 'Đã đến điểm này';
  static const back = 'Quay lại';
  static String allStops(int n) => 'Tất cả điểm dừng ($n)';
  static const depotChip = 'Kho';
  static String zoneChip(String names) => 'Vùng: $names';
  static const notesPrefix = 'Ghi chú:';
  static const fallbackLegend = 'Đường đi chưa có từ máy chủ (chỉ minh hoạ)';
  static const googleAttribution = '© Google';
  static const zoomIn = 'Phóng to';
  static const zoomOut = 'Thu nhỏ';
  static const fitRoute = 'Xem cả tuyến';

  // Location
  static const followMe = 'Theo dõi vị trí';
  static const locationDialogTitle = 'Hiển thị vị trí của bạn';
  static const locationExplain =
      'Vị trí chỉ hiển thị trên bản đồ của bạn, không gửi về máy chủ';
  static const later = 'Để sau';
  static const continueLabel = 'Tiếp tục';
  static const locationDenied =
      'Chưa có quyền vị trí. Bạn có thể bật trong Cài đặt của máy.';
  static const openSettings = 'Mở cài đặt';

  // Errors (design §6)
  static const forbidden = 'Bạn không có quyền xem tuyến này.';
  static const notFound = 'Không tìm thấy tuyến.';
  static const network = 'Không kết nối được máy chủ';
  static const clientError = 'Yêu cầu không hợp lệ. Vui lòng thử lại.';
  static const retry = 'Thử lại';
}
