package cn.course.shopping.account;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** 格式与第一阶段 Python PBKDF2-SHA256 兼容，已有数据库密码无需重置。 */
public final class Passwords {
    private static final SecureRandom RANDOM = new SecureRandom();
    private Passwords() {}
    public static String hash(String password) {
        byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
        String saltText = HexFormat.of().formatHex(salt);
        return "260000$" + saltText + "$" + HexFormat.of().formatHex(derive(password, saltText, 260000));
    }
    public static boolean verify(String password, String stored) {
        try {
            String[] parts = stored.split("\\$");
            if (parts.length != 3) return false;
            int rounds = Integer.parseInt(parts[0]);
            if (rounds < 1 || rounds > 2_000_000) return false;
            return MessageDigest.isEqual(derive(password, parts[1], rounds), HexFormat.of().parseHex(parts[2]));
        } catch (IllegalArgumentException error) { return false; }
    }
    private static byte[] derive(String password, String salt, int rounds) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), rounds, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException error) { throw new IllegalStateException(error); }
        finally { spec.clearPassword(); }
    }
    public static String token() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static String tokenHash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
