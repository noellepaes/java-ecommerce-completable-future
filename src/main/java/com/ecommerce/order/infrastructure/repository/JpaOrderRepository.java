package com.ecommerce.order.infrastructure.repository;

import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.repository.OrderRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaOrderRepository extends JpaRepository<Order, UUID>, OrderRepository {

    /**
     * Carrega {@code items} no mesmo SELECT (evita N+1 / LazyInitializationException
     * ao montar {@code OrderDTO} com open-in-view desligado).
     */
    @Override
    @EntityGraph(attributePaths = "items")
    Optional<Order> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = "items")
    List<Order> findByCustomerId(UUID customerId);

    @Override
    @EntityGraph(attributePaths = "items")
    List<Order> findAll();
}
