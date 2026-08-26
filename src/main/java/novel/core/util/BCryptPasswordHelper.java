package novel.core.util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;
@Slf4j
@Component
@RequiredArgsConstructor
public class BCryptPasswordHelper {
    private final PasswordEncoder passwordEncoder;

    //判断是否匹配
       public boolean matches(String rawPassword, String dbPassword) {
        if (dbPassword == null || rawPassword == null) {
            return false;
        }

        return passwordEncoder.matches(rawPassword, dbPassword);
    }

    /**
     * 密码加密（BCrypt）
     */
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}





