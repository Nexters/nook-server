package org.every.nook.api.application.place

object HangulOcrRuleSpec {
    const val MAX_EDIT_DISTANCE = 2
    const val MAX_ERROR_RATIO_DENOMINATOR = 3
}

object PlaceCandidateRuleSpec {
    const val MIN_GROUNDING_KEY_LENGTH = 2
    const val MIN_NAME_COMPATIBILITY_KEY_LENGTH = 3
    const val MIN_FUZZY_NAME_LENGTH = 4
    const val MAX_NAME_CHARACTER_DIFFERENCE = 1
    const val MIN_NEAR_OCR_NAME_LENGTH = 3
    const val MAX_OCR_NAME_EDIT_DISTANCE = 3
    const val MIN_ADDRESS_GROUNDING_KEY_LENGTH = 6
    const val MIN_SEARCH_IDENTITY_LENGTH = 2
}

object PlaceParsingRuleSpec {
    const val MAX_PLACE_COUNT = 60
    const val MAX_QUERY_COUNT = 4
    const val MAX_IMAGE_COUNT = 20
    const val MIN_EXPECTED_PLACE_COUNT = 2
    const val MAX_EXPECTED_PLACE_COUNT = 80
}
