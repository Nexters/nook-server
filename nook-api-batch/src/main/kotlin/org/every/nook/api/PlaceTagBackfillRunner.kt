package org.every.nook.api

import org.every.nook.api.application.place.RebuildPlaceTagsUseCase
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "place-tag-backfill", name = ["enabled"], havingValue = "true")
class PlaceTagBackfillRunner(
    private val rebuildPlaceTags: RebuildPlaceTagsUseCase,
    @Value("\${place-tag-backfill.post-ids:}") private val postIds: Set<Long>,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        logger.info("Place tag backfill started: postIds={}", postIds.ifEmpty { "ALL" })
        val result = rebuildPlaceTags(postIds)
        logger.info(
            "Place tag backfill completed: succeeded={}, failed={}, failedPostIds={}",
            result.succeeded,
            result.failed,
            result.failedPostIds,
        )
        check(result.failed == 0) { "Place tag backfill contains failed targets: ${result.failed}" }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(PlaceTagBackfillRunner::class.java)
    }
}
