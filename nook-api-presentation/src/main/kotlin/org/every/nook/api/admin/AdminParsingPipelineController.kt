package org.every.nook.api.admin

import org.every.nook.api.application.admin.GetAdminParsingPipelineUseCase
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/v1/parsing-pipeline")
class AdminParsingPipelineController(private val getParsingPipeline: GetAdminParsingPipelineUseCase) {
    @GetMapping
    fun parsingPipeline(@RequestParam(required = false) postId: Long?): ApiResponse<*> =
        ApiResponse.success(getParsingPipeline(postId))
}
