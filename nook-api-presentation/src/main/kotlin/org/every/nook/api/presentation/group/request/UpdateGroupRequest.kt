package org.every.nook.api.presentation.group.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UpdateGroupRequest(
    @field:Schema(description = "그룹명", example = "서울 카페", maxLength = 20)
    @field:NotBlank
    @field:Size(max = 20)
    val name: String,
    @field:Schema(
        description = "그룹 색상 코드",
        example = "GREEN",
        allowableValues = ["YELLOW", "CORAL", "PINK", "PURPLE", "BLUE", "MINT", "GREEN", "GRAY"],
    )
    @field:Pattern(regexp = "YELLOW|CORAL|PINK|PURPLE|BLUE|MINT|GREEN|GRAY")
    val color: String,
)
