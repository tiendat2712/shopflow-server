package org.shopflow.backend.presentation.dto

import org.shopflow.backend.domain.exception.AccountInactiveException
import org.shopflow.backend.domain.exception.DuplicateCategorySlugException
import org.shopflow.backend.domain.exception.DuplicateProductSkuException
import org.shopflow.backend.domain.exception.DuplicateProductSlugException
import org.shopflow.backend.domain.exception.ProductConcurrencyConflictException
import org.shopflow.backend.domain.exception.UserAlreadyExistsException

/**
 * Centralized API response codes for success and error payloads.
 */
object ApiCode {
    // Success codes
    const val SYSTEM_STATUS_SUCCESS = "SYSTEM_STATUS_SUCCESS"

    const val AUTH_REGISTER_SUCCESS = "AUTH_REGISTER_SUCCESS"
    const val AUTH_LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESS"
    const val AUTH_REFRESH_SUCCESS = "AUTH_REFRESH_SUCCESS"
    const val AUTH_ME_SUCCESS = "AUTH_ME_SUCCESS"
    const val AUTH_PROFILE_UPDATE_SUCCESS = "AUTH_PROFILE_UPDATE_SUCCESS"

    const val CATEGORY_LIST_SUCCESS = "CATEGORY_LIST_SUCCESS"
    const val CATEGORY_PRODUCT_LIST_SUCCESS = "CATEGORY_PRODUCT_LIST_SUCCESS"
    const val CATEGORY_CREATE_SUCCESS = "CATEGORY_CREATE_SUCCESS"

    const val PRODUCT_LIST_SUCCESS = "PRODUCT_LIST_SUCCESS"
    const val PRODUCT_DETAIL_SUCCESS = "PRODUCT_DETAIL_SUCCESS"
    const val PRODUCT_CREATE_SUCCESS = "PRODUCT_CREATE_SUCCESS"
    const val PRODUCT_UPDATE_SUCCESS = "PRODUCT_UPDATE_SUCCESS"
    const val PRODUCT_PUBLISH_SUCCESS = "PRODUCT_PUBLISH_SUCCESS"
    const val PRODUCT_RESTORE_SUCCESS = "PRODUCT_RESTORE_SUCCESS"

    const val ADMIN_PRODUCT_LIST_SUCCESS = "ADMIN_PRODUCT_LIST_SUCCESS"
    const val ADMIN_PRODUCT_DETAIL_SUCCESS = "ADMIN_PRODUCT_DETAIL_SUCCESS"
    const val ADMIN_CATEGORY_LIST_SUCCESS = "ADMIN_CATEGORY_LIST_SUCCESS"
    const val ADMIN_CATEGORY_PRODUCT_LIST_SUCCESS = "ADMIN_CATEGORY_PRODUCT_LIST_SUCCESS"

    const val MEDIA_UPLOAD_SUCCESS = "MEDIA_UPLOAD_SUCCESS"
    const val DATASET_IMPORT_SUCCESS = "DATASET_IMPORT_SUCCESS"

    // Domain & business error codes
    const val USER_ALREADY_EXISTS = UserAlreadyExistsException.ERROR_CODE
    const val USER_NOT_FOUND = "USER_NOT_FOUND"
    const val ROLE_NOT_FOUND = "ROLE_NOT_FOUND"
    const val PERMISSION_NOT_FOUND = "PERMISSION_NOT_FOUND"
    const val CATEGORY_NOT_FOUND = "CATEGORY_NOT_FOUND"
    const val DUPLICATE_CATEGORY_SLUG = DuplicateCategorySlugException.ERROR_CODE
    const val PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND"
    const val DUPLICATE_PRODUCT_SKU = DuplicateProductSkuException.ERROR_CODE
    const val DUPLICATE_PRODUCT_SLUG = DuplicateProductSlugException.ERROR_CODE
    const val PRODUCT_CONCURRENCY_CONFLICT = ProductConcurrencyConflictException.ERROR_CODE
    const val INVALID_MEDIA_FILE = "INVALID_MEDIA_FILE"
    const val IMAGE_STORAGE_ERROR = "IMAGE_STORAGE_ERROR"
    const val DATASET_IMPORT_ERROR = "DATASET_IMPORT_ERROR"
    const val APPLICATION_ERROR = "APPLICATION_ERROR"

    // Auth & security error codes
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val INVALID_TOKEN = "INVALID_TOKEN"
    const val ACCOUNT_INACTIVE = AccountInactiveException.ERROR_CODE
    const val ACCESS_DENIED = "ACCESS_DENIED"

    // Request, validation & framework error codes
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
    const val MALFORMED_JSON = "MALFORMED_JSON"
    const val MISSING_REQUEST_BODY = "MISSING_REQUEST_BODY"
    const val INVALID_PARAMETER_TYPE = "INVALID_PARAMETER_TYPE"
    const val MISSING_PARAMETER = "MISSING_PARAMETER"
    const val MISSING_MULTIPART_PART = "MISSING_MULTIPART_PART"
    const val ENDPOINT_NOT_FOUND = "ENDPOINT_NOT_FOUND"
    const val METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED"
    const val UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE"
    const val PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE"
    const val INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR"

    // Validation detail codes (used in errors[].code)
    const val REQUIRED_FIELD = "REQUIRED_FIELD"
    const val INVALID_EMAIL = "INVALID_EMAIL"
    const val INVALID_PASSWORD = "INVALID_PASSWORD"
    const val INVALID_PRICE = "INVALID_PRICE"
    const val INVALID_FORMAT = "INVALID_FORMAT"
    const val INVALID_LENGTH = "INVALID_LENGTH"
    const val VALUE_OUT_OF_RANGE = "VALUE_OUT_OF_RANGE"
    const val TYPE_MISMATCH = "TYPE_MISMATCH"
    const val INVALID_VALUE = "INVALID_VALUE"
    const val INVALID_OBJECT = "INVALID_OBJECT"
    const val FILE_TOO_LARGE = "FILE_TOO_LARGE"
}
