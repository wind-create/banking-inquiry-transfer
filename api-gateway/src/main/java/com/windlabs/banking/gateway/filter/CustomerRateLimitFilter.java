package com.windlabs.banking.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class CustomerRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log =
            LoggerFactory.getLogger(CustomerRateLimitFilter.class);

    private static final String LUA = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local member = ARGV[3]

            local time = redis.call('TIME')
            local now = time[1] * 1000 + math.floor(time[2] / 1000)

            redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)

            local count = redis.call('ZCARD', key)
            local allowed = 0

            if count < limit then
                redis.call('ZADD', key, now, member)
                redis.call('PEXPIRE', key, window)
                count = count + 1
                allowed = 1
            end

            local retry = 0

            if allowed == 0 then
                local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                retry = math.max(0, tonumber(oldest[2]) + window - now)
            end

            return {allowed, limit - count, retry}
            """;

    private final ReactiveStringRedisTemplate redis;
    private final RedisScript<List> script;
    private final int maxRequests;
    private final long windowMillis;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public CustomerRateLimitFilter(
            ReactiveStringRedisTemplate redis,
            @Value("${app.rate-limit.max-requests:5}") int maxRequests,
            @Value("${app.rate-limit.window:5m}") Duration window
    ) {
        if (maxRequests < 1 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Invalid rate limit configuration");
        }

        this.redis = redis;
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();

        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(LUA);
        redisScript.setResultType(List.class);
        this.script = redisScript;
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain
    ) {
        return exchange.getPrincipal()
                .ofType(Authentication.class)
                .flatMap(authentication -> {
                    if (!(authentication instanceof JwtAuthenticationToken jwt)) {
                        return reject(exchange, HttpStatus.UNAUTHORIZED);
                    }

                    String customerId = jwt.getToken().getSubject();

                    if (customerId == null || customerId.isBlank()) {
                        return reject(exchange, HttpStatus.FORBIDDEN);
                    }

                    String key = "rate-limit:customer:" + customerId;

                    Mono<List> decision = redis.execute(
                                    script,
                                    List.of(key),
                                    List.of(
                                            String.valueOf(maxRequests),
                                            String.valueOf(windowMillis),
                                            UUID.randomUUID().toString()
                                    )
                            )
                            .next()
                            .switchIfEmpty(Mono.error(
                                    new IllegalStateException("Empty Redis response")
                            ))
                            .onErrorResume(error -> {
                                log.error("Rate limiter unavailable", error);
                                return Mono.just(List.of(-1L, 0L, 0L));
                            });

                    return decision.flatMap(result -> {
                        long allowed = ((Number) result.get(0)).longValue();
                        long remaining = ((Number) result.get(1)).longValue();
                        long retryMillis = ((Number) result.get(2)).longValue();

                        if (allowed < 0) {
                            return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE);
                        }

                        exchange.getResponse().getHeaders()
                                .set("X-RateLimit-Limit", String.valueOf(maxRequests));

                        exchange.getResponse().getHeaders()
                                .set("X-RateLimit-Remaining", String.valueOf(remaining));

                        if (allowed == 0) {
                            long retrySeconds = Math.max(
                                    1,
                                    (retryMillis + 999) / 1000
                            );

                            exchange.getResponse().getHeaders()
                                    .set("Retry-After", String.valueOf(retrySeconds));

                            return reject(exchange, HttpStatus.TOO_MANY_REQUESTS);
                        }

                        return chain.filter(exchange);
                    });
                })
                .switchIfEmpty(Mono.defer(() ->
                        reject(exchange, HttpStatus.UNAUTHORIZED)
                ));
    }

    private Mono<Void> reject(
            ServerWebExchange exchange,
            HttpStatus status
    ) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}