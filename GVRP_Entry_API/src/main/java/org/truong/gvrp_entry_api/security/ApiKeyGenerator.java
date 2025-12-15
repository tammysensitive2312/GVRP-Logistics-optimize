package org.truong.gvrp_entry_api.security;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ApiKeyGenerator {

    /**
     * Độ dài của API Key (tính bằng bytes trước khi mã hóa Base64).
     * 32 bytes (256 bits) là tiêu chuẩn tốt. Sau Base64, chuỗi sẽ dài khoảng 44 ký tự.
     */
    private static final int KEY_LENGTH_BYTES = 32;

    /**
     * Sinh ra một API Key bảo mật.
     * Phương pháp:
     * 1. Dùng SecureRandom để tạo ra một mảng bytes ngẫu nhiên, cryptographically strong.
     * 2. Mã hóa mảng bytes đó bằng Base64 URL-safe để tạo ra một chuỗi ký tự ASCII hợp lệ, dễ sử dụng.
     * * @return Chuỗi API Key ngẫu nhiên.
     */
    public static String generateSecureApiKey() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] keyBytes = new byte[KEY_LENGTH_BYTES];

        // 1. Tạo ngẫu nhiên các byte
        secureRandom.nextBytes(keyBytes);

        // 2. Mã hóa Base64 URL-safe (loại bỏ ký tự '+' và '/' gây rắc rối trong URL)
        // và loại bỏ padding '=' ở cuối.
        String apiKey = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(keyBytes);

        return apiKey;
    }

    public static void main(String[] args) {
        System.out.println("--- GVRP Internal API Key Generator ---");
        System.out.println("Generating new secure API Key...");

        String newKey = generateSecureApiKey();

        System.out.println("\n✅ New API Key (Length: " + newKey.length() + " characters):");
        System.out.println(newKey);

        System.out.println("\n📌 Hướng dẫn sử dụng:");
        System.out.println("1. Dán key này vào cấu hình application.properties:");
        System.out.println("   gvrp.security.internal-api-key=" + newKey);
        System.out.println("2. Cấu hình Engine API để gửi key này trong header 'X-Internal-Secret'.");
    }
}
