package com.ecommerce.auth.application.usecase;

import com.ecommerce.auth.application.dto.UserSummaryDTO;
import com.ecommerce.auth.domain.model.User;
import com.ecommerce.auth.domain.repository.UserRepository;
import com.ecommerce.config.AsyncExecutorConfig;
import com.ecommerce.customer.domain.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Lista users e hidrata nomes de customer em paralelo.
 * Sem {@code @Transactional} no método externo: cada find do Spring Data
 * abre sua própria TX — evita deadlock de pool JDBC com CompletableFuture.
 */
@Service
public class ListUsersUseCase {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final Executor ioTaskExecutor;

    public ListUsersUseCase(
            UserRepository userRepository,
            CustomerRepository customerRepository,
            @Qualifier(AsyncExecutorConfig.IO_TASK_EXECUTOR) Executor ioTaskExecutor) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.ioTaskExecutor = ioTaskExecutor;
    }

    public List<UserSummaryDTO> execute() {
        List<User> users = userRepository.findAll();

        List<CompletableFuture<UserSummaryDTO>> futures = users.stream()
                .map(user -> {
                    String email = user.getEmail();
                    return CompletableFuture.supplyAsync(
                            () -> customerRepository.findByEmail(email)
                                    .map(customer -> new UserSummaryDTO(email, customer.getName()))
                                    .orElse(new UserSummaryDTO(email, email)),
                            ioTaskExecutor);
                })
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        return futures.stream().map(CompletableFuture::join).toList();
    }
}
