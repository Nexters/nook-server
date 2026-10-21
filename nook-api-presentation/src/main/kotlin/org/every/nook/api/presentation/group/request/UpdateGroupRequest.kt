package org.every.nook.api.presentation.group.request

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.every.nook.api.application.group.GroupDescriptionPolicy

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
    @field:Schema(
        description = "선택 소개글. 앞뒤 공백 제거 후 최대 150자이며 줄바꿈과 조합 이모지를 허용합니다. " +
            "생략 또는 null이면 유지, 빈 문자열이나 공백만 보내면 삭제합니다.",
        nullable = true,
    )
    val description: String? = null,
) {
    @get:JsonIgnore
    @get:Schema(hidden = true)
    @get:AssertTrue(message = "소개글은 사용자에게 보이는 글자 기준 최대 150자까지 입력할 수 있습니다")
    val isDescriptionValid: Boolean
        get() = GroupDescriptionPolicy.isValid(description)
}
