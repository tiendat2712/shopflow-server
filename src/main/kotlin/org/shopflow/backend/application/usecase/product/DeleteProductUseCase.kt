package org.shopflow.backend.application.usecase.product

import org.shopflow.backend.application.command.product.DeleteProductCommand
import org.shopflow.backend.domain.event.DomainEventPublisher
import org.shopflow.backend.domain.exception.ProductNotFoundException
import org.shopflow.backend.domain.model.product.ProductId
import org.shopflow.backend.domain.repository.product.ProductRepository
import org.shopflow.backend.domain.service.TimeProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeleteProductUseCase(
    private val productRepository: ProductRepository,
    private val timeProvider: TimeProvider,
    private val domainEventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(command: DeleteProductCommand) {
        val productId = ProductId.fromString(command.productId)
        val existing =
            productRepository.findById(productId)
                ?: throw ProductNotFoundException()

        val (archived, archivedEvent) = existing.archive(timeProvider.now())
        productRepository.save(archived)
        domainEventPublisher.publish(archivedEvent)
    }
}
