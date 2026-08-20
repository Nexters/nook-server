package org.every.nook.api.application.place

class RebuildPlaceTagsUseCase(
    private val backfillPort: PlaceTagBackfillPort,
    private val storePlaceTags: StorePlaceTagsUseCase,
) {
    operator fun invoke(postIds: Set<Long> = emptySet()): Result {
        var succeeded = 0
        val failedPostIds = mutableListOf<Long>()
        backfillPort.findAll()
            .filter { postIds.isEmpty() || it.postId in postIds }
            .forEach { event ->
                runCatching { storePlaceTags(event) }
                    .onSuccess { succeeded += 1 }
                    .onFailure { failedPostIds += event.postId }
            }
        return Result(succeeded = succeeded, failedPostIds = failedPostIds)
    }

    data class Result(val succeeded: Int, val failedPostIds: List<Long>) {
        val failed: Int = failedPostIds.size
    }
}
