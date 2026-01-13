package org.truong.gvrp_entry_api.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordEncoderUtil {

    public static void main(String[] args) {
        // Mật khẩu bạn muốn mã hóa
        String rawPassword = "hoang";

        // Hoặc bạn có thể lấy mật khẩu từ đối số dòng lệnh nếu thích:
        // if (args.length == 0) {
        //     System.out.println("Vui lòng nhập mật khẩu cần mã hóa.");
        //     return;
        // }
        // String rawPassword = args[0];
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String encodedPassword = encoder.encode(rawPassword);

        System.out.println("==========================================");
        System.out.println("Mật khẩu thô (Raw Password): " + rawPassword);
        System.out.println("Chuỗi BCrypt đã mã hóa (Encoded Hash):");
        System.out.println(encodedPassword);
        System.out.println("==========================================");

        // Kiểm tra xem mã hóa có hợp lệ không (Optional)
        boolean matches = encoder.matches(rawPassword, encodedPassword);
        System.out.println("Kiểm tra xác thực (Nên là true): " + matches);
    }
}
