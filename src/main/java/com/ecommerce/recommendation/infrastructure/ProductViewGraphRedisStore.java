package com.ecommerce.recommendation.infrastructure;

import com.ecommerce.config.AsyncExecutorConfig;
import com.ecommerce.recommendation.config.RecommendationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Associação usuário ↔ produto só no Redis (sem entidade JPA):
 * <ul>
 *   <li>{@code ecommerce:views:user:{customerId}} — SET de productIds que o cliente viu/clicou</li>
 *   <li>{@code ecommerce:views:product:{productId}} — SET de customerIds que viram o produto</li>
 * </ul>
 * Cada escrita renova o TTL nas duas chaves (dados efêmeros para recomendação).
 * <p>
 * {@link #recordView} grava os dois ramos (user↔product) em paralelo — são independentes.
 */
@Component
public class ProductViewGraphRedisStore {

    private static final String USER_VIEWS_PREFIX = "ecommerce:views:user:";
    private static final String PRODUCT_VIEWERS_PREFIX = "ecommerce:views:product:";

    private final StringRedisTemplate redisTemplate;
    private final RecommendationProperties recommendationProperties;
    private final Executor ioTaskExecutor;

    public ProductViewGraphRedisStore(
            StringRedisTemplate redisTemplate,
            RecommendationProperties recommendationProperties,
            @Qualifier(AsyncExecutorConfig.IO_TASK_EXECUTOR) Executor ioTaskExecutor) {
        this.redisTemplate = redisTemplate;
        this.recommendationProperties = recommendationProperties;
        this.ioTaskExecutor = ioTaskExecutor;
    }

    public void recordView(UUID customerId, UUID productId) {
        String userKey = userViewsKey(customerId);
        String productKey = productViewersKey(productId);
        Duration ttl = recommendationProperties.customerHistoryTtl();

        CompletableFuture<Void> userSide = CompletableFuture.runAsync(() -> {
            redisTemplate.opsForSet().add(userKey, productId.toString());
            redisTemplate.expire(userKey, ttl);
        }, ioTaskExecutor);

        CompletableFuture<Void> productSide = CompletableFuture.runAsync(() -> {
            redisTemplate.opsForSet().add(productKey, customerId.toString());
            redisTemplate.expire(productKey, ttl);
        }, ioTaskExecutor);

        CompletableFuture.allOf(userSide, productSide).join();
    }

    public Set<UUID> getUserViewedProductIds(UUID customerId) {
        return parseUuidSet(redisTemplate.opsForSet().members(userViewsKey(customerId)));
    }

    public Set<UUID> getProductViewerIds(UUID productId) {
        return parseUuidSet(redisTemplate.opsForSet().members(productViewersKey(productId)));
    }

    private static Set<UUID> parseUuidSet(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptySet();
        }
        return raw.stream().map(UUID::fromString).collect(Collectors.toSet());
    }

    private static String userViewsKey(UUID customerId) {
        return USER_VIEWS_PREFIX + customerId;
    }

    private static String productViewersKey(UUID productId) {
        return PRODUCT_VIEWERS_PREFIX + productId;
    }
}
