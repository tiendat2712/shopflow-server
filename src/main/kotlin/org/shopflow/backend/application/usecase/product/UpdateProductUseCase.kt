package org.shopflow.backend.application.usecase.product

import org.shopflow.backend.application.command.product.UpdateProductCommand
import org.shopflow.backend.application.dto.product.ProductDetailDto
import org.shopflow.backend.application.dto.product.toDetailDto
import org.shopflow.backend.domain.event.DomainEventPublisher
import org.shopflow.backend.domain.exception.CategoryNotFoundException
import org.shopflow.backend.domain.exception.DuplicateProductSkuException
import org.shopflow.backend.domain.exception.DuplicateProductSlugException
import org.shopflow.backend.domain.exception.ProductNotFoundException
import org.shopflow.backend.domain.model.product.CategoryId
import org.shopflow.backend.domain.model.product.Money
import org.shopflow.backend.domain.model.product.ProductId
import org.shopflow.backend.domain.model.product.ProductStatus
import org.shopflow.backend.domain.model.product.Sku
import org.shopflow.backend.domain.model.product.Slug
import org.shopflow.backend.domain.repository.product.CategoryRepository
import org.shopflow.backend.domain.repository.product.ProductRepository
import org.shopflow.backend.domain.service.TimeProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateProductUseCase(
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository,
    private val timeProvider: TimeProvider,
    private val domainEventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(command: UpdateProductCommand): ProductDetailDto {
        val productId = ProductId.fromString(command.productId)
        val existing =
            productRepository.findById(productId)
                ?: throw ProductNotFoundException()

        val categoryId = CategoryId.fromString(command.categoryId)
        val category =
            categoryRepository.findById(categoryId)
                ?: throw CategoryNotFoundException()

        val sku = Sku.of(command.sku)
        if (sku != existing.sku && productRepository.existsBySku(sku)) {
            throw DuplicateProductSkuException("Product SKU '${sku.value}' already exists.")
        }

        val slug = Slug.of(command.slug?.takeIf { it.isNotBlank() } ?: command.name)
        if (slug != existing.slug && productRepository.existsBySlug(slug)) {
            throw DuplicateProductSlugException("Product slug '${slug.value}' already exists.")
        }

        val (updated, updatedEvent) =
            existing.update(
                categoryId = category.id,
                name = command.name,
                slug = slug,
                sku = sku,
                description = command.description,
                price = Money.of(command.price),
                stockQuantity = command.stockQuantity,
                imageUrl = command.imageUrl,
                status = ProductStatus.fromString(command.status),
                now = timeProvider.now(),
            )

        val saved = productRepository.save(updated)
        domainEventPublisher.publish(updatedEvent)
        return saved.toDetailDto(category)
    }
}
