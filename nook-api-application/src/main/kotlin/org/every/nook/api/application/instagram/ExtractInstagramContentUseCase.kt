package org.every.nook.api.application.instagram

class ExtractInstagramContentUseCase(private val provider: InstagramContentProvider) {
    operator fun invoke(url: String): ExtractedInstagramContent = provider.extract(InstagramContentUrl.parse(url))
}
