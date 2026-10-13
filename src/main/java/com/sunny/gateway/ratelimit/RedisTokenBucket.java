package com.sunny.gateway.ratelimit;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
public class RedisTokenBucket {
    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>();
    static {
        SCRIPT.setLocation(new ClassPathResource("ratelimit/token-bucket.lua"));
        SCRIPT.setResultType(List.class);
    }
    private final StringRedisTemplate redis;
    public RedisTokenBucket(StringRedisTemplate redis) { this.redis = redis; }
    public Decision consume(UUID tenantId, RateLimitProperties.Policy policy) {
        List<?> result = redis.execute(SCRIPT, List.of(key(tenantId)), Long.toString(policy.capacity()),
                Double.toString(policy.refillRate()), Long.toString(policy.requestCost()));
        if (result == null || result.size() != 4) throw new IllegalStateException("Invalid rate limit result");
        return new Decision(((Number) result.get(0)).longValue() == 1, ((Number) result.get(1)).longValue(),
                ((Number) result.get(2)).longValue(), ((Number) result.get(3)).longValue());
    }
    public static String key(UUID tenantId) { return "gateway:ratelimit:v1:{" + tenantId + "}:chat"; }
    public record Decision(boolean allowed, long remaining, long retryAfter, long resetAfter) {}
}
