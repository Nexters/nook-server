package org.every.nook.api.application.place

internal fun List<PlaceClue>.reconcileWithNumberedPlaceCards(transcripts: List<ImageTranscript>): List<PlaceClue> {
    val cards = transcripts.flatMap(ImageTranscript::numberedPlaceCards)
    if (cards.isEmpty()) return this
    val normalizedClues = asSequence()
        .filterNot { clue -> clue.isRepeatedPublisherMark(transcripts, cards) }
        .map { clue -> clue.restoreNumberedCardName(cards) }
        .toList()
    return (cards.map(NumberedPlaceCard::toPlaceClue) + normalizedClues)
        .distinctBy { clue -> clue.name.numberedCardIdentity() }
}

private fun ImageTranscript.numberedPlaceCards(): List<NumberedPlaceCard> = texts.flatMap { text ->
    NUMBERED_PLACE_CARD_PATTERN.findAll(text).mapNotNull { match ->
        val name = match.groupValues[1].trim().trim('-', '·', '•')
        val region = match.groupValues[2].trim()
        if (name.length !in MIN_CARD_NAME_LENGTH..MAX_CARD_NAME_LENGTH ||
            name.split(Regex("\\s+")).size > MAX_CARD_NAME_WORDS
        ) {
            null
        } else {
            NumberedPlaceCard(imageIndex, name, region, text)
        }
    }.toList()
}

private fun PlaceClue.isRepeatedPublisherMark(
    transcripts: List<ImageTranscript>,
    cards: List<NumberedPlaceCard>,
): Boolean {
    val identity = name.numberedCardIdentity().takeIf { it.length >= MIN_MARK_IDENTITY_LENGTH } ?: return false
    if (cards.any { card -> card.name.numberedCardIdentity() == identity }) return false
    val markedImageIndexes = transcripts.filter { transcript ->
        transcript.texts.any { text -> text.numberedCardIdentity().contains(identity) }
    }.map(ImageTranscript::imageIndex).toSet()
    val cardImageIndexes = cards.map(NumberedPlaceCard::imageIndex).toSet()
    return markedImageIndexes.intersect(cardImageIndexes).size >= MIN_REPEATED_MARK_IMAGE_COUNT
}

private fun PlaceClue.restoreNumberedCardName(cards: List<NumberedPlaceCard>): PlaceClue {
    val evidenceImageIndexes = evidence.map(PlaceClueEvidence::imageIndex).toSet()
    val nameWithoutSequence = name.replaceFirst(CARD_SEQUENCE_PREFIX_PATTERN, "").numberedCardIdentity()
    val matchedCard = cards.singleOrNull { card ->
        card.imageIndex in evidenceImageIndexes && card.name.numberedCardIdentity() == nameWithoutSequence
    } ?: return this
    return copy(
        name = matchedCard.name,
        region = region ?: matchedCard.region,
        queries = (listOf(matchedCard.name, "${matchedCard.region} ${matchedCard.name}") + queries).distinct(),
    )
}

private fun NumberedPlaceCard.toPlaceClue(): PlaceClue = PlaceClue(
    name = name,
    region = region,
    queries = listOf(name, "$region $name"),
    evidence = listOf(PlaceClueEvidence(imageIndex, evidenceText)),
)

private fun String.numberedCardIdentity(): String = lowercase().filter(Char::isLetterOrDigit)

private data class NumberedPlaceCard(
    val imageIndex: Int,
    val name: String,
    val region: String,
    val evidenceText: String,
)

private val NUMBERED_PLACE_CARD_PATTERN = Regex(
    "(?:^|\\s)\\d{1,2}\\s*([가-힣A-Za-z][^|ㅣ\\n]{0,29}?)\\s*[|ㅣ]\\s*([^\\s|ㅣ,.;!?]{1,20})",
)
private val CARD_SEQUENCE_PREFIX_PATTERN = Regex("^\\s*\\d{1,2}\\s*")
private const val MIN_CARD_NAME_LENGTH = 2
private const val MAX_CARD_NAME_LENGTH = 30
private const val MAX_CARD_NAME_WORDS = 3
private const val MIN_MARK_IDENTITY_LENGTH = 2
private const val MIN_REPEATED_MARK_IMAGE_COUNT = 2
