package org.every.nook.api.application.save.error

import org.every.nook.api.application.error.NookException

class SavedPostNotFoundException : NookException(SavedPostErrorCode.SAVED_POST_NOT_FOUND)
