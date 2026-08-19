package org.every.nook.api.application.admin

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UpdateAdminPlaceUseCaseTest {
    @Test
    fun `trims editable values and delegates correction context`() {
        var captured: AdminPlaceCorrectionPort.UpdateCommand? = null
        val useCase = UpdateAdminPlaceUseCase(
            object : AdminPlaceCorrectionPort {
                override fun update(command: AdminPlaceCorrectionPort.UpdateCommand): AdminPlaceDetail {
                    captured = command
                    return detail(command.placeId, command.name, command.address)
                }
            },
        )

        val result = useCase(
            UpdateAdminPlaceUseCase.Command(
                placeId = 17,
                name = "  누크 카페  ",
                address = "  서울 성동구 누크로 17  ",
                actor = AdminActor("subject", "admin@everynook.co.kr"),
                reason = "  주소 오기 정정  ",
                requestId = "request-17",
            ),
        )

        assertEquals("누크 카페", result.name)
        assertEquals("서울 성동구 누크로 17", captured?.address)
        assertEquals("주소 오기 정정", captured?.reason)
        assertEquals("request-17", captured?.requestId)
    }

    @Test
    fun `rejects a blank correction reason`() {
        val useCase = UpdateAdminPlaceUseCase(
            object : AdminPlaceCorrectionPort {
                override fun update(command: AdminPlaceCorrectionPort.UpdateCommand): AdminPlaceDetail =
                    detail(command.placeId, command.name, command.address)
            },
        )

        assertFailsWith<IllegalArgumentException> {
            useCase(
                UpdateAdminPlaceUseCase.Command(
                    placeId = 17,
                    name = "누크 카페",
                    address = "서울 성동구 누크로 17",
                    actor = AdminActor("subject", "admin@everynook.co.kr"),
                    reason = " ",
                    requestId = null,
                ),
            )
        }
    }

    private fun detail(placeId: Long, name: String, address: String) = AdminPlaceDetail(
        id = placeId,
        name = name,
        address = address,
        provider = "KAKAO",
        externalPlaceId = "external-17",
        linkedPostCount = 1,
        affectedUserCount = 2,
        posts = listOf(
            AdminLinkedPost(
                id = 7,
                title = "게시글",
                authorIdentifier = "author",
                canonicalUrl = "https://example.com/post/7",
                createdAt = Instant.EPOCH,
            ),
        ),
    )
}
