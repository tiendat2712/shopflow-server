package org.shopflow.backend.infrastructure.storage.cloudinary

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "cloudinary")
class CloudinaryProperties {
    var cloudName: String = ""
    var apiKey: String = ""
    var apiSecret: String = ""
    var secure: Boolean = true
    var rootFolder: String = "shopflow"

    fun isConfigured(): Boolean =
        cloudName.isNotBlank() &&
            apiKey.isNotBlank() &&
            apiSecret.isNotBlank()
}
