package org.shopflow.backend.application.usecase.product

import org.shopflow.backend.application.command.product.PublishProductCommand
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
class PublishProductUseCase(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val timeProvider: TimeProvider,
    private val domainEventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(command: PublishProductCommand): ProductDetailDto {
        val productId = ProductId.fromString(command.productId)
        val existing =
            productRepository.findById(productId)
                ?: throw ProductNotFoundException()

        val (published, publishedEvent) = existing.publish(command.expectedVersion, timeProvider.now())
        val category =
            categoryRepository.findById(existing.categoryId)
                ?: throw CategoryNotFoundException()

        if (publishedEvent == null) {
            return existing.toDetailDto(category)
        }

        val saved = productRepository.update(published)
        domainEventPublisher.publish(publishedEvent)
        return saved.toDetailDto(category)
    }
}
