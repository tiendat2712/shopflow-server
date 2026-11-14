package org.shopflow.backend.application.usecase.product

import org.shopflow.backend.application.command.product.CreateProductCommand
import org.shopflow.backend.application.dto.product.ProductDetailDto
import org.shopflow.backend.application.dto.product.toDetailDto
import org.shopflow.backend.domain.event.DomainEventPublisher
import org.shopflow.backend.domain.exception.CategoryNotFoundException
import org.shopflow.backend.domain.exception.DuplicateProductSkuException
import org.shopflow.backend.domain.exception.DuplicateProductSlugException
import org.shopflow.backend.domain.model.product.CategoryId
import org.shopflow.backend.domain.model.product.Money
import org.shopflow.backend.domain.model.product.Product
import org.shopflow.backend.domain.model.product.ProductStatus
import org.shopflow.backend.domain.model.product.Sku
import org.shopflow.backend.domain.model.product.Slug
import org.shopflow.backend.domain.repository.product.CategoryRepository
import org.shopflow.backend.domain.repository.product.ProductRepository
import org.shopflow.backend.domain.service.TimeProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateProductUseCase(
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository,
    private val timeProvider: TimeProvider,
    private val domainEventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(command: CreateProductCommand): ProductDetailDto {
        val categoryId = CategoryId.fromString(command.categoryId)
        val category =
            categoryRepository.findById(categoryId)
                ?: throw CategoryNotFoundException()

        val sku = Sku.of(command.sku)
        if (productRepository.existsBySku(sku)) {
            throw DuplicateProductSkuException("Product SKU '${sku.value}' already exists.")
        }

        val slug = Slug.of(command.slug?.takeIf { it.isNotBlank() } ?: command.name)
        if (productRepository.existsBySlug(slug)) {
            throw DuplicateProductSlugException("Product slug '${slug.value}' already exists.")
        }

        val (product, createdEvent) =
            Product.create(
                categoryId = category.id,
                name = command.name,
                slug = slug,
                sku = sku,
                price = Money.of(command.price),
                now = timeProvider.now(),
                description = command.description,
                stockQuantity = command.stockQuantity,
                imageUrl = command.imageUrl,
                status = ProductStatus.fromString(command.status),
            )

        val saved = productRepository.save(product)
        domainEventPublisher.publish(createdEvent)
        return saved.toDetailDto(category)
    }
}
