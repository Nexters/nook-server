package org.every.nook.api.application.content

class ExtractPostContentUseCase(private val extractors: List<PostContentExtractor>) {
    operator fun invoke(url: String): ExtractedPostContent {
        val extractor = extractors.firstOrNull { it.supports(url) }
            ?: throw UnsupportedPostUrlException()
        return extractor.extract(url)
    }
}
