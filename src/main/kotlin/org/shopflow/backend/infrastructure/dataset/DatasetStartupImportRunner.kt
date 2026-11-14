package org.shopflow.backend.infrastructure.dataset

import org.shopflow.backend.application.command.dataset.ImportKaggleDatasetCommand
import org.shopflow.backend.application.usecase.dataset.ImportKaggleDatasetUseCase
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Optional startup runner that executes the Kaggle dataset import when `app.dataset.auto-import-on-startup=true`.
 */
@Component
@Order(200)
class DatasetStartupImportRunner(
    private val datasetProperties: DatasetProperties,
    private val importKaggleDatasetUseCase: ImportKaggleDatasetUseCase,
    private val applicationContext: ConfigurableApplicationContext,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(DatasetStartupImportRunner::class.java)

    override fun run(args: ApplicationArguments) {
        if (!datasetProperties.autoImportOnStartup) {
            return
        }

        val result = importKaggleDatasetUseCase.execute(ImportKaggleDatasetCommand())
        logger.info(
            "Startup dataset import finished in {}ms | cloudinaryLiveUploadUsed={} | usersInserted={} | usersSkipped={} | " +
                "categoriesInserted={} | categoriesExisting={} | productsInserted={} | productsSkipped={} | " +
                "productsImageUpdated={} | ordersInserted={} | ordersSkipped={} | orderItemsInserted={} | " +
                "orderItemsSkipped={} | reviewsInserted={} | reviewsSkipped={} | productReviewCountsUpdated={} | " +
                "userEventsInserted={} | userEventsSkipped={} | stagedCsvFiles={} | categoryImageUrls={}",
            result.durationMs,
            result.cloudinaryLiveUploadUsed,
            result.usersInserted,
            result.usersSkipped,
            result.categoriesInserted,
            result.categoriesExisting,
            result.productsInserted,
            result.productsSkipped,
            result.productsImageUpdated,
            result.ordersInserted,
            result.ordersSkipped,
            result.orderItemsInserted,
            result.orderItemsSkipped,
            result.reviewsInserted,
            result.reviewsSkipped,
            result.productReviewCountsUpdated,
            result.userEventsInserted,
            result.userEventsSkipped,
            result.stagedCsvFiles,
            result.categoryImageUrls,
        )
    }

    @EventListener(ApplicationReadyEvent::class)
    fun shutdownIfRequested() {
        if (!datasetProperties.autoImportOnStartup || !datasetProperties.exitAfterStartupImport) {
            return
        }

        // Close cleanly after ApplicationReadyEvent completes so one-shot CLI imports terminate with exit code 0.
        Thread(
            {
                Thread.sleep(200)
                applicationContext.close()
            },
            "dataset-import-shutdown",
        ).start()
    }
}
