package org.every.nook.api.application.admin

import org.every.nook.api.domain.place.PlaceTag
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UpdateAdminPlaceTagUseCaseTest {
    @Test
    fun `normalizes editable values and keeps the stable tag code`() {
        var captured: AdminPlaceTagCatalogPort.UpdateCommand? = null
        val useCase = UpdateAdminPlaceTagUseCase(
            port { command ->
                captured = command
                view(command)
            },
        )

        val result = useCase(
            command(
                displayName = "  고요한  ",
                matchingKeywords = listOf(" 조용 ", "조용", " 고요한 "),
                reason = "  기획 변경  ",
            ),
        )

        assertEquals(PlaceTag.QUIET, captured?.tagCode)
        assertEquals("고요한", result.displayName)
        assertEquals(listOf("조용", "고요한"), captured?.matchingKeywords)
        assertEquals("기획 변경", captured?.reason)
    }

    @Test
    fun `does not allow legacy compatibility tags to enter the managed catalog`() {
        val useCase = UpdateAdminPlaceTagUseCase(port { view(it) })

        assertFailsWith<IllegalArgumentException> {
            useCase(command(tagCode = PlaceTag.FAST.name))
        }
    }

    private fun port(update: (AdminPlaceTagCatalogPort.UpdateCommand) -> AdminPlaceTagDefinition) =
        object : AdminPlaceTagCatalogPort {
            override fun list(
                category: org.every.nook.api.domain.place.PlaceTagCategory?,
                enabled: Boolean?,
                offset: Int,
                limit: Int,
            ) = AdminPage<AdminPlaceTagDefinition>(emptyList(), 0)

            override fun update(command: AdminPlaceTagCatalogPort.UpdateCommand) = update(command)
        }

    private fun command(
        tagCode: String = PlaceTag.QUIET.name,
        displayName: String = "조용한",
        matchingKeywords: List<String> = listOf("조용"),
        reason: String = "수정",
    ) = UpdateAdminPlaceTagUseCase.Command(
        tagCode = tagCode,
        category = "ATMOSPHERE",
        displayName = displayName,
        matchingKeywords = matchingKeywords,
        enabled = true,
        sortOrder = 3,
        actor = AdminActor("subject", "admin@everynook.co.kr"),
        reason = reason,
        requestId = "request-3",
    )

    private fun view(command: AdminPlaceTagCatalogPort.UpdateCommand) = AdminPlaceTagDefinition(
        id = command.tagCode.name,
        tagCode = command.tagCode.name,
        category = command.category.name,
        displayName = command.displayName,
        matchingKeywords = command.matchingKeywords,
        enabled = command.enabled,
        sortOrder = command.sortOrder,
        updatedAt = Instant.EPOCH,
    )
}
