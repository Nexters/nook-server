package org.every.nook.api.application.place

import mu.KotlinLogging

internal class ImageClueRecallRecovery(
    private val retranscribe: (List<ImageTextExtractor.ImageInput>) -> List<ImageTranscript>,
    private val storeTranscripts: (List<ImageTranscript>) -> Unit,
    private val extractClues: (List<ImageTranscript>) -> List<PlaceClue>,
) {
    fun recover(request: Request): List<PlaceClue> {
        val expectedPlaceCount = request.expectedPlaceCount
        if (expectedPlaceCount == null || request.knownPlaceCount + request.primaryClues.size >= expectedPlaceCount) {
            return emptyList()
        }
        val usedImageIndexes = request.primaryClues
            .flatMap { clue -> clue.evidence.map(PlaceClueEvidence::imageIndex) }
            .toSet()
        val unusedImages = request.images.filterNot { image -> image.imageIndex in usedImageIndexes }
        if (unusedImages.isEmpty()) return emptyList()

        return runCatching {
            logger.info {
                "Place clue recall recovery started: postId=${request.postId}, attempt=${request.attempt}, " +
                    "expectedPlaceCount=$expectedPlaceCount, knownPlaceCount=${request.knownPlaceCount}, " +
                    "primaryImageClueCount=${request.primaryClues.size}, unusedImageCount=${unusedImages.size}"
            }
            recoverUnusedImages(request, unusedImages)
        }.getOrElse { exception ->
            logger.warn(exception) {
                "Place clue recall recovery skipped after failure: postId=${request.postId}, " +
                    "attempt=${request.attempt}, reason=${exception.message}"
            }
            emptyList()
        }
    }

    private fun recoverUnusedImages(
        request: Request,
        unusedImages: List<ImageTextExtractor.ImageInput>,
    ): List<PlaceClue> {
        val transcriptByIndex = request.transcripts.associateBy(ImageTranscript::imageIndex).toMutableMap()
        val imagesToRetranscribe = unusedImages
            .filter { image -> transcriptByIndex[image.imageIndex].hasInsufficientText() }
            .take(MAX_RECOVERY_TRANSCRIPT_COUNT)
        if (imagesToRetranscribe.isNotEmpty()) {
            retranscribe(imagesToRetranscribe).forEach { recovered ->
                val existing = transcriptByIndex[recovered.imageIndex]
                transcriptByIndex[recovered.imageIndex] = ImageTranscript(
                    imageIndex = recovered.imageIndex,
                    texts = (existing?.texts.orEmpty() + recovered.texts).distinct(),
                )
            }
            storeTranscripts(
                request.images.map { image ->
                    transcriptByIndex[image.imageIndex] ?: ImageTranscript(image.imageIndex, emptyList())
                },
            )
        }
        val recoveryTranscripts = unusedImages.mapNotNull { image ->
            transcriptByIndex[image.imageIndex]?.takeIf { transcript -> transcript.texts.isNotEmpty() }
        }
        return recoveryTranscripts.takeIf(List<ImageTranscript>::isNotEmpty)?.let(extractClues).orEmpty()
    }

    data class Request(
        val postId: Long,
        val attempt: Int,
        val images: List<ImageTextExtractor.ImageInput>,
        val transcripts: List<ImageTranscript>,
        val primaryClues: List<PlaceClue>,
        val knownPlaceCount: Int,
        val expectedPlaceCount: Int?,
    )

    private companion object {
        val logger = KotlinLogging.logger {}
        const val MAX_RECOVERY_TRANSCRIPT_COUNT = 5
    }
}

internal fun extractImageTranscripts(
    extractor: ImageTextExtractor,
    images: List<ImageTextExtractor.ImageInput>,
): List<ImageTranscript> = images.chunked(IMAGE_BATCH_SIZE).flatMap { batch ->
    val requestedIndexes = batch.map(ImageTextExtractor.ImageInput::imageIndex)
    val transcripts = extractor.extract(ImageTextExtractor.Request(batch))
    val requestedIndexSet = requestedIndexes.toSet()
    val responseIndexes = transcripts.map(ImageTranscript::imageIndex)
    val missingIndexes = requestedIndexes.filterNot(responseIndexes::contains)
    val duplicateIndexes = responseIndexes.groupingBy { it }.eachCount().filterValues { count -> count > 1 }.keys
    val unexpectedIndexes = responseIndexes.filterNot(requestedIndexSet::contains).distinct()
    if (missingIndexes.isNotEmpty() || duplicateIndexes.isNotEmpty() || unexpectedIndexes.isNotEmpty()) {
        logger.warn {
            "Image transcript response normalized: requestedIndexes=$requestedIndexes, " +
                "missingIndexes=$missingIndexes, duplicateIndexes=$duplicateIndexes, " +
                "unexpectedIndexes=$unexpectedIndexes"
        }
    }
    val textsByIndex = transcripts.asSequence()
        .filter { transcript -> transcript.imageIndex in requestedIndexSet }
        .groupBy(ImageTranscript::imageIndex)
        .mapValues { (_, sameIndexTranscripts) -> sameIndexTranscripts.flatMap(ImageTranscript::texts).distinct() }
    requestedIndexes.map { imageIndex ->
        ImageTranscript(imageIndex = imageIndex, texts = textsByIndex[imageIndex].orEmpty())
    }
}.sortedBy(ImageTranscript::imageIndex)

internal fun List<PlaceClue>.filterGroundedImageClues(
    imageCount: Int,
    postId: Long,
    attempt: Int,
    recovered: Boolean,
): List<PlaceClue> = filter { clue ->
    clue.hasImageEvidence(imageCount).also { grounded ->
        if (!grounded) {
            logger.warn {
                "Ungrounded ${if (recovered) "recovered " else ""}image place clue skipped: " +
                    "postId=$postId, attempt=$attempt, placeName=${clue.name}, evidence=${clue.evidence}"
            }
        }
    }
}

private fun ImageTranscript?.hasInsufficientText(): Boolean = this == null || texts.size <= 1

private fun PlaceClue.hasImageEvidence(imageCount: Int): Boolean = evidence.any { evidence ->
    evidence.imageIndex in 1..imageCount && evidence.evidenceText.isNotBlank()
}

private val logger = KotlinLogging.logger {}
private const val IMAGE_BATCH_SIZE = 5
