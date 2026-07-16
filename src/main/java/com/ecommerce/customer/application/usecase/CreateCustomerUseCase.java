package com.ecommerce.customer.application.usecase;

import com.ecommerce.config.AsyncExecutorConfig;
import com.ecommerce.customer.application.dto.CustomerDTO;
import com.ecommerce.customer.domain.model.Customer;
import com.ecommerce.customer.domain.repository.CustomerRepository;
import com.ecommerce.shared.exception.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class CreateCustomerUseCase {

    private final CustomerRepository repository;
    private final Executor ioTaskExecutor;

    public CreateCustomerUseCase(
            CustomerRepository repository,
            @Qualifier(AsyncExecutorConfig.IO_TASK_EXECUTOR) Executor ioTaskExecutor) {
        this.repository = repository;
        this.ioTaskExecutor = ioTaskExecutor;
    }

    public CustomerDTO execute(CustomerDTO customerDTO) {
        // email e CPF são checagens independentes → thenCombine / allOf
        CompletableFuture<Boolean> emailTaken = CompletableFuture.supplyAsync(
                () -> repository.findByEmail(customerDTO.email()).isPresent(),
                ioTaskExecutor);
        CompletableFuture<Boolean> cpfTaken = CompletableFuture.supplyAsync(
                () -> repository.findByCpf(customerDTO.cpf()).isPresent(),
                ioTaskExecutor);

        CompletableFuture.allOf(emailTaken, cpfTaken).join();

        if (Boolean.TRUE.equals(emailTaken.join())) {
            throw new BusinessException("Cliente com este email já existe");
        }
        if (Boolean.TRUE.equals(cpfTaken.join())) {
            throw new BusinessException("Cliente com este CPF já existe");
        }

        Customer customer = new Customer();
        customer.setName(customerDTO.name());
        customer.setEmail(customerDTO.email());
        customer.setCpf(customerDTO.cpf());

        customer = repository.save(customer);
        return CustomerDTO.from(customer);
    }
}
