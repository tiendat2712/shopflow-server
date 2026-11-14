package org.shopflow.backend.application.exception

open class ApplicationException(
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
