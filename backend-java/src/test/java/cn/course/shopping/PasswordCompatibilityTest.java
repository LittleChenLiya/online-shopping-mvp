package cn.course.shopping;

import cn.course.shopping.account.Passwords;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 验证标准向量和独立 Node crypto 生成的 UTF-8 向量，覆盖旧版哈希格式。 */
class PasswordCompatibilityTest {
    @Test void verifiesPbkdf2Sha256KnownVector() {
        assertTrue(Passwords.verify("password", "1$salt$120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b"));
    }
    @Test void verifiesUnicodePasswordFromIndependentImplementation() {
        String stored = "260000$0123456789abcdef0123456789abcdef$840f9703bbf2a8ffd17cc2fb6f5db4bc6746c49a30c1e73e9fb645084d1bc40d";
        assertTrue(Passwords.verify("卖家🔒密码", stored));
        assertFalse(Passwords.verify("wrong", stored));
    }
    @Test void randomSaltAndMalformedHashes() {
        String first = Passwords.hash("seller12345"), second = Passwords.hash("seller12345");
        assertNotEquals(first, second); assertTrue(Passwords.verify("seller12345", first));
        for (String malformed : new String[]{"plaintext", "0$salt$ff", "999999999$salt$ff", "1$salt$xyz"})
            assertFalse(Passwords.verify("password", malformed));
    }
}
