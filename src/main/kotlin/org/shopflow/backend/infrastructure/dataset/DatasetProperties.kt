package org.shopflow.backend.infrastructure.dataset

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "app.dataset")
class DatasetProperties {
    var localDir: String = "data/ecommerce_dataset"
    var kaggleSlug: String = "abhayayare/e-commerce-dataset"
    var kaggleUsername: String = ""
    var kaggleKey: String = ""
    var defaultUserPassword: String = "ShopFlow@123!"
    var autoImportOnStartup: Boolean = false
    var exitAfterStartupImport: Boolean = false
}
