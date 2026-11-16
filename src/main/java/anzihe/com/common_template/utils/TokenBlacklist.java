package anzihe.com.common_template.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 已登出 Token 的黑名单。
 *
 * 为什么放在内存里：这个模板没有引入 Redis，单实例部署下用一张 ConcurrentHashMap 就够了。
 * 加 Redis 只是为了登出黑名单不划算，还要多维护一个中间件。
 *
 * 代价（写在这里，免得以后踩）：
 * 1. 服务重启后黑名单清空，已登出的旧 token 在剩余有效期内会重新可用；
 * 2. 多实例部署时不共享，A 实例登出不影响 B 实例。
 * 真需要这两点时，把 revoke / isRevoked 换成 Redis 的 SETEX + EXISTS 即可，调用方无需改动。
 */
@Slf4j
@Component
public class TokenBlacklist {

    /** token -> 过期时间戳(ms)。token 自然过期后这条记录就没有意义了 */
    private final Map<String, Long> revokedTokens = new ConcurrentHashMap<>();

    /** 超过这个量就顺手清理一次过期项，避免长期运行只增不减 */
    private static final int CLEAN_THRESHOLD = 512;

    /** token 解不出过期时间时的兜底保留时长，与默认配置 expire-minutes 保持一致 */
    private static final long DEFAULT_TTL_MILLIS = 30 * 60 * 1000L;

    /**
     * 登记一个已登出的 token
     */
    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        long expireAt = resolveExpireAt(token);
        if (expireAt <= System.currentTimeMillis()) {
            // token 本身已经过期，登记没有意义
            return;
        }
        revokedTokens.put(token, expireAt);
        if (revokedTokens.size() > CLEAN_THRESHOLD) {
            purgeExpired();
        }
    }

    /**
     * 该 token 是否已被登出
     */
    public boolean isRevoked(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Long expireAt = revokedTokens.get(token);
        if (expireAt == null) {
            return false;
        }
        if (expireAt <= System.currentTimeMillis()) {
            revokedTokens.remove(token);
            return false;
        }
        return true;
    }

    private long resolveExpireAt(String token) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            Date expiresAt = jwt.getExpiresAt();
            return expiresAt == null
                    ? System.currentTimeMillis() + DEFAULT_TTL_MILLIS
                    : expiresAt.getTime();
        } catch (Exception e) {
            // 连 JWT 都解不开，说明 token 本身不合法，签名校验阶段就会拒绝，这里不必登记
            log.warn("登出时解析 token 失败，跳过登记: {}", e.getMessage());
            return 0L;
        }
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        revokedTokens.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}
