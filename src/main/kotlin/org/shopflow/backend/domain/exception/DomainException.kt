package org.shopflow.backend.domain.exception

sealed class DomainException(
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class UserAlreadyExistsException(
    message: String = "A user with that email already exists.",
) : DomainException(message)

class InvalidCredentialsException(
    message: String = "Invalid credentials.",
) : DomainException(message)

class InvalidTokenException(
    message: String = "Invalid refresh token.",
) : DomainException(message)

class UserNotFoundException(
    message: String = "User not found.",
) : DomainException(message)

class AccountInactiveException(
    message: String = "User account is inactive.",
) : DomainException(message)

class DomainValidationException(
    message: String,
) : DomainException(message)

class RoleNotFoundException(
    message: String = "Role not found.",
) : DomainException(message)

class PermissionNotFoundException(
    message: String = "Permission not found.",
) : DomainException(message)

class CategoryNotFoundException(
    message: String = "Category not found.",
) : DomainException(message)

class DuplicateCategorySlugException(
    message: String = "A category with that slug already exists.",
) : DomainException(message)

class ProductNotFoundException(
    message: String = "Product not found.",
) : DomainException(message)

class DuplicateProductSkuException(
    message: String = "A product with that SKU already exists.",
) : DomainException(message)

class DuplicateProductSlugException(
    message: String = "A product with that slug already exists.",
) : DomainException(message)

class InvalidMediaFileException(
    message: String,
) : DomainException(message)

class ImageStorageException(
    message: String,
    cause: Throwable? = null,
) : DomainException(message, cause)

class DatasetImportException(
    message: String,
    cause: Throwable? = null,
) : DomainException(message, cause)
