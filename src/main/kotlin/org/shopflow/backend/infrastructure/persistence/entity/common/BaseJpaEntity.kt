package org.shopflow.backend.infrastructure.persistence.entity.common

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Transient
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.domain.Persistable
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseJpaEntity(
    @field:Id
    @field:Column(name = "id", nullable = false, length = 36)
    private val entityId: String,
) : Persistable<String> {
    @field:CreatedDate
    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @field:LastModifiedDate
    @field:Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null

    @field:CreatedBy
    @field:Column(name = "created_by", length = 100, updatable = false)
    var createdBy: String? = null

    @field:LastModifiedBy
    @field:Column(name = "updated_by", length = 100)
    var updatedBy: String? = null

    @field:Transient
    private var isNewEntity: Boolean = true

    override fun getId(): String = entityId

    @Transient
    override fun isNew(): Boolean = isNewEntity

    @PostPersist
    @PostLoad
    protected open fun markPersisted() {
        isNewEntity = false
    }
}
