package org.shopflow.backend.application.usecase.product

import org.shopflow.backend.application.command.product.RestoreProductCommand
import org.shopflow.backend.application.dto.product.ProductDetailDto
import org.shopflow.backend.application.dto.product.toDetailDto
import org.shopflow.backend.domain.event.DomainEventPublisher
import org.shopflow.backend.domain.exception.CategoryNotFoundException
import org.shopflow.backend.domain.exception.ProductNotFoundException
import org.shopflow.backend.domain.model.product.ProductId
import org.shopflow.backend.domain.repository.product.CategoryRepository
import org.shopflow.backend.domain.repository.product.ProductRepository
import org.shopflow.backend.domain.service.TimeProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RestoreProductUseCase(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val timeProvider: TimeProvider,
    private val domainEventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(command: RestoreProductCommand): ProductDetailDto {
        val productId = ProductId.fromString(command.productId)
        val existing =
            productRepository.findById(productId)
                ?: throw ProductNotFoundException()

        val (restored, restoredEvent) = existing.restore(command.expectedVersion, timeProvider.now())
        val category =
            categoryRepository.findById(existing.categoryId)
                ?: throw CategoryNotFoundException()

        val saved = productRepository.update(restored)
        domainEventPublisher.publish(restoredEvent)
        return saved.toDetailDto(category)
    }
}
