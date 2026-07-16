package com.ecommerce.recommendation.application;

import com.ecommerce.config.AsyncExecutorConfig;
import com.ecommerce.product.application.dto.ProductDTO;
import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.model.ProductStatus;
import com.ecommerce.product.domain.repository.ProductRepository;
import com.ecommerce.recommendation.config.RecommendationProperties;
import com.ecommerce.recommendation.infrastructure.ProductViewGraphRedisStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Recomendação colaborativa: fan-out Redis + hidratação Postgres.
 * Chamadas independentes rodam em paralelo via {@link CompletableFuture#allOf}.
 */
@Service
public class GetPurchaseRecommendationsUseCase {

    private final ProductRepository productRepository;
    private final ProductViewGraphRedisStore viewGraphStore;
    private final RecommendationProperties recommendationProperties;
    private final Executor ioTaskExecutor;

    public GetPurchaseRecommendationsUseCase(
            ProductRepository productRepository,
            ProductViewGraphRedisStore viewGraphStore,
            RecommendationProperties recommendationProperties,
            @Qualifier(AsyncExecutorConfig.IO_TASK_EXECUTOR) Executor ioTaskExecutor) {
        this.productRepository = productRepository;
        this.viewGraphStore = viewGraphStore;
        this.recommendationProperties = recommendationProperties;
        this.ioTaskExecutor = ioTaskExecutor;
    }

    public List<ProductDTO> execute(UUID customerId) {
        Set<UUID> userViews = viewGraphStore.getUserViewedProductIds(customerId);
        int limit = recommendationProperties.suggestionLimit();

        Map<UUID, Integer> scores = collaborativeScoresParallel(customerId, userViews);
        List<UUID> rankedCandidateIds = scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .filter(id -> !userViews.contains(id))
                .limit(limit * 3L)
                .toList();

        List<ProductDTO> result = hydrateActiveProductsParallel(rankedCandidateIds, limit);
        Set<UUID> already = new HashSet<>(userViews);
        result.forEach(dto -> already.add(dto.id()));

        if (result.size() < limit) {
            fillFromCatalogExcluding(already, limit - result.size(), result);
        }

        return result;
    }

    /**
     * Fase 1: viewers de cada produto visto (independentes).
     * Fase 2: histórico de cada peer (independentes).
     * Depois agrega scores em memória.
     */
    private Map<UUID, Integer> collaborativeScoresParallel(UUID customerId, Set<UUID> userViews) {
        Map<UUID, Integer> scores = new HashMap<>();
        if (userViews.isEmpty()) {
            return scores;
        }

        List<CompletableFuture<Set<UUID>>> viewerFutures = userViews.stream()
                .map(productId -> CompletableFuture.supplyAsync(
                        () -> viewGraphStore.getProductViewerIds(productId),
                        ioTaskExecutor))
                .toList();

        CompletableFuture.allOf(viewerFutures.toArray(CompletableFuture[]::new)).join();

        Set<UUID> peers = new HashSet<>();
        for (CompletableFuture<Set<UUID>> future : viewerFutures) {
            for (UUID peerId : future.join()) {
                if (!peerId.equals(customerId)) {
                    peers.add(peerId);
                }
            }
        }

        if (peers.isEmpty()) {
            return scores;
        }

        List<CompletableFuture<Set<UUID>>> peerViewFutures = peers.stream()
                .map(peerId -> CompletableFuture.supplyAsync(
                        () -> viewGraphStore.getUserViewedProductIds(peerId),
                        ioTaskExecutor))
                .toList();

        CompletableFuture.allOf(peerViewFutures.toArray(CompletableFuture[]::new)).join();

        for (CompletableFuture<Set<UUID>> peerViewsFuture : peerViewFutures) {
            for (UUID candidateId : peerViewsFuture.join()) {
                if (userViews.contains(candidateId)) {
                    continue;
                }
                scores.merge(candidateId, 1, Integer::sum);
            }
        }
        return scores;
    }

    private List<ProductDTO> hydrateActiveProductsParallel(List<UUID> candidateIds, int limit) {
        if (candidateIds.isEmpty() || limit <= 0) {
            return new ArrayList<>();
        }

        List<CompletableFuture<Optional<ProductDTO>>> futures = candidateIds.stream()
                .map(productId -> CompletableFuture.supplyAsync(
                        () -> productRepository.findById(productId)
                                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                                .filter(Product::isAvailable)
                                .map(ProductDTO::from),
                        ioTaskExecutor))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        List<ProductDTO> result = new ArrayList<>();
        for (CompletableFuture<Optional<ProductDTO>> future : futures) {
            if (result.size() >= limit) {
                break;
            }
            future.join().ifPresent(result::add);
        }
        return result;
    }

    private void fillFromCatalogExcluding(Set<UUID> excludeIds, int need, List<ProductDTO> into) {
        if (need <= 0) {
            return;
        }
        productRepository.findByStatus(ProductStatus.ACTIVE).stream()
                .filter(Product::isAvailable)
                .filter(p -> !excludeIds.contains(p.getId()))
                .limit(need)
                .map(ProductDTO::from)
                .forEach(dto -> {
                    into.add(dto);
                    excludeIds.add(dto.id());
                });
    }
}
