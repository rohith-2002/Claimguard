package com.claimguard.screening;

import com.claimguard.api.ClaimRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis sorted-set history store. Each claim is a member scored by its reportedAt epoch millis.
 *
 * <p>Because trimming uses ZREMRANGEBYSCORE relative to the claim being recorded, input must arrive in
 * reportedAt order for the windows to stay exact.
 */
@Component
@ConditionalOnProperty(name = "claimguard.history.store", havingValue = "redis")
public class RedisClaimHistoryStore implements ClaimHistoryStore {

    private static final Duration POLICY_WINDOW = Duration.ofDays(180);
    private static final Duration VEHICLE_WINDOW = Duration.ofDays(90);
    private static final Duration PROVIDER_WINDOW = Duration.ofDays(7);
    private static final Duration TTL_SLACK = Duration.ofDays(30);

    private final StringRedisTemplate redis;

    public RedisClaimHistoryStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean record(ClaimRequest c) {
        boolean isNew = add("claims:policy:" + c.policyNumber(), c.claimId(), c.reportedAt(), POLICY_WINDOW);
        if (c.vehicleRegNo() != null) {
            add("claims:vehicle:" + c.vehicleRegNo(), c.policyNumber() + "|" + c.claimId(),
                    c.reportedAt(), VEHICLE_WINDOW);
        }
        add("claims:provider:" + c.providerId(), c.claimId(), c.reportedAt(), PROVIDER_WINDOW);
        return isNew;
    }

    /** ZADD (score = reportedAt epoch millis), ZREMRANGEBYSCORE older than the window, EXPIRE. */
    private boolean add(String key, String member, Instant reportedAt, Duration window) {
        long score = reportedAt.toEpochMilli();
        Boolean added = redis.opsForZSet().add(key, member, score);
        redis.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, score - window.toMillis() - 1);
        redis.expire(key, window.plus(TTL_SLACK));
        return Boolean.TRUE.equals(added);
    }

    @Override
    public int countPolicyClaims(String policy, Instant from, Instant to, String excludeClaimId) {
        return countExcluding("claims:policy:" + policy, excludeClaimId, from.toEpochMilli(), to.toEpochMilli() - 1);
    }

    @Override
    public int countProviderClaims(String provider, Instant from, Instant to, String excludeClaimId) {
        return countExcluding("claims:provider:" + provider, excludeClaimId, from.toEpochMilli(), to.toEpochMilli());
    }

    @Override
    public int countVehicleClaimsOnOtherPolicies(String vehicle, String policy, Instant from, Instant to) {
        Set<String> members = redis.opsForZSet()
                .rangeByScore("claims:vehicle:" + vehicle, from.toEpochMilli(), to.toEpochMilli() - 1);
        if (members == null) {
            return 0;
        }
        return (int) members.stream().filter(m -> !m.startsWith(policy + "|")).count();
    }

    private int countExcluding(String key, String excludeMember, long min, long max) {
        Long n = redis.opsForZSet().count(key, min, max);
        Double self = redis.opsForZSet().score(key, excludeMember);
        boolean selfInRange = self != null && self >= min && self <= max;
        return (n == null ? 0 : n.intValue()) - (selfInRange ? 1 : 0);
    }
}
