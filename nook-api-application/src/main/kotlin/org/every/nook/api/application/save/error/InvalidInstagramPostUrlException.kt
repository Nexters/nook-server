package org.every.nook.api.application.save.error

import org.every.nook.api.application.error.NookException

class InvalidInstagramPostUrlException : NookException(SavedPostErrorCode.INVALID_INSTAGRAM_POST_URL)
