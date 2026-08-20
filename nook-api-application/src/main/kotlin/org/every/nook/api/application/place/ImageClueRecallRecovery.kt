package org.every.nook.api.application.place

import mu.KotlinLogging
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

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
    concurrency: Int = DEFAULT_IMAGE_OCR_CONCURRENCY,
    fallbackImage: (ImageTextExtractor.ImageInput) -> ImageTextExtractor.ImageInput? = { null },
): List<ImageTranscript> {
    require(concurrency > 0) { "Image OCR concurrency must be positive" }
    if (images.isEmpty()) return emptyList()
    val executor = Executors.newFixedThreadPool(minOf(concurrency, images.size))
    return executor.use {
        val results = images.map { image ->
            executor.submit(Callable { extractSingleImage(extractor, image, fallbackImage) })
        }.map { it.get() }
        val failures = results.mapNotNull(SingleImageTranscriptResult::exceptionOrNull)
        if (failures.size == results.size) throw requireNotNull(failures.firstOrNull())
        results.map(SingleImageTranscriptResult::transcript).sortedBy(ImageTranscript::imageIndex)
    }
}

internal fun extractTranscriptsWithLatestUrlFallback(
    postId: Long,
    images: List<ImageTextExtractor.ImageInput>,
    extractor: ImageTextExtractor,
    imageUrlPort: PlaceImageUrlPort,
    concurrency: Int,
): List<ImageTranscript> {
    val latestImages = AtomicReference<List<ImageTextExtractor.ImageInput>?>(null)
    return extractImageTranscripts(
        extractor = extractor,
        images = images,
        concurrency = concurrency,
        fallbackImage = { failedImage ->
            latestImages.updateAndGet { existing ->
                existing ?: imageUrlPort.findImageUrls(postId).take(MAX_IMAGE_COUNT).mapIndexed { index, url ->
                    ImageTextExtractor.ImageInput(index + 1, url)
                }
            }.orEmpty().firstOrNull { it.imageIndex == failedImage.imageIndex }
                ?.takeIf { it.imageUrl != failedImage.imageUrl }
        },
    )
}

private fun extractSingleImage(
    extractor: ImageTextExtractor,
    image: ImageTextExtractor.ImageInput,
    fallbackImage: (ImageTextExtractor.ImageInput) -> ImageTextExtractor.ImageInput?,
): SingleImageTranscriptResult {
    val primary = runCatching { extractor.extract(ImageTextExtractor.Request(listOf(image))).normalize(image) }
    if (primary.isSuccess) return SingleImageTranscriptResult(primary.getOrThrow(), null)
    val fallback = fallbackImage(image)
    val recovered = fallback?.let {
        logger.info { "Image transcript retried with refreshed URL: imageIndex=${image.imageIndex}" }
        runCatching { extractor.extract(ImageTextExtractor.Request(listOf(it))).normalize(it) }
    }
    val result = recovered ?: primary
    return result.fold(
        onSuccess = { SingleImageTranscriptResult(it, null) },
        onFailure = { exception ->
            logger.warn(exception) { "Image transcript failed independently: imageIndex=${image.imageIndex}" }
            SingleImageTranscriptResult(ImageTranscript(image.imageIndex, emptyList()), exception)
        },
    )
}

private fun List<ImageTranscript>.normalize(image: ImageTextExtractor.ImageInput): ImageTranscript {
    val matching = filter { it.imageIndex == image.imageIndex }
    val unexpectedIndexes = map(ImageTranscript::imageIndex).filter { it != image.imageIndex }.distinct()
    if (matching.size != 1 || unexpectedIndexes.isNotEmpty()) {
        logger.warn {
            "Single image transcript response normalized: requestedIndex=${image.imageIndex}, " +
                "responseCount=${matching.size}, unexpectedIndexes=$unexpectedIndexes"
        }
    }
    return ImageTranscript(
        imageIndex = image.imageIndex,
        texts = matching.flatMap(ImageTranscript::texts).distinct(),
    )
}

private data class SingleImageTranscriptResult(val transcript: ImageTranscript, private val exception: Throwable?) {
    fun exceptionOrNull(): Throwable? = exception
}

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
private const val DEFAULT_IMAGE_OCR_CONCURRENCY = 4
private const val MAX_IMAGE_COUNT = 20
