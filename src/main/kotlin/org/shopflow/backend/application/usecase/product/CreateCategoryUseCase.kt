package org.shopflow.backend.application.usecase.product

import org.shopflow.backend.application.command.product.CreateCategoryCommand
import org.shopflow.backend.application.dto.product.CategoryDto
import org.shopflow.backend.application.dto.product.toDto
import org.shopflow.backend.domain.event.DomainEventPublisher
import org.shopflow.backend.domain.exception.CategoryNotFoundException
import org.shopflow.backend.domain.exception.DuplicateCategorySlugException
import org.shopflow.backend.domain.model.product.Category
import org.shopflow.backend.domain.model.product.CategoryId
import org.shopflow.backend.domain.model.product.Slug
import org.shopflow.backend.domain.repository.product.CategoryRepository
import org.shopflow.backend.domain.service.TimeProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateCategoryUseCase(
    private val categoryRepository: CategoryRepository,
    private val timeProvider: TimeProvider,
    private val domainEventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(command: CreateCategoryCommand): CategoryDto {
        val slug = Slug.of(command.slug?.takeIf { it.isNotBlank() } ?: command.name, Slug.CATEGORY_MAX_LENGTH)
        if (categoryRepository.existsBySlug(slug)) {
            throw DuplicateCategorySlugException("Category slug '${slug.value}' already exists.")
        }

        val parentId =
            command.parentId
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { rawParentId ->
                    val parsedId = CategoryId.fromString(rawParentId)
                    if (categoryRepository.findById(parsedId) == null) {
                        throw CategoryNotFoundException("Parent category not found.")
                    }
                    parsedId
                }

        val (category, createdEvent) =
            Category.create(
                name = command.name,
                slug = slug,
                now = timeProvider.now(),
                parentId = parentId,
                description = command.description,
                displayOrder = command.displayOrder,
                isActive = command.isActive,
            )

        val saved = categoryRepository.save(category)
        domainEventPublisher.publish(createdEvent)
        return saved.toDto()
    }
}
