package org.every.nook.api.application.group

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.error.InvalidGroupException
import org.every.nook.api.application.group.port.GroupPort
import org.every.nook.api.domain.group.GroupColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GroupUseCasesTest {
    @Test
    fun `creates a trimmed group with an allowed color`() {
        val port = FakeGroupPort()

        val created = CreateGroupUseCase(port)(
            CreateGroupUseCase.Command(userId = 7, name = "  카페  ", color = "YELLOW"),
        )

        assertEquals("카페", created.name)
        assertEquals("YELLOW", created.color)
    }

    @Test
    fun `allows duplicate names within the same user`() {
        val port = FakeGroupPort()

        val first = CreateGroupUseCase(port)(
            CreateGroupUseCase.Command(userId = 7, name = "카페", color = "YELLOW"),
        )
        val second = CreateGroupUseCase(port)(
            CreateGroupUseCase.Command(userId = 7, name = "카페", color = "GREEN"),
        )

        assertEquals("카페", first.name)
        assertEquals("카페", second.name)
        assertEquals(1, first.id)
        assertEquals(2, second.id)
    }

    @Test
    fun `rejects unsupported color codes`() {
        assertFailsWith<InvalidGroupException> {
            CreateGroupUseCase(FakeGroupPort())(
                CreateGroupUseCase.Command(userId = 7, name = "카페", color = "ORANGE"),
            )
        }
    }

    @Test
    fun `reports inaccessible groups as not found on update and delete`() {
        val port = FakeGroupPort(
            updateResult = GroupPort.UpdateResult.NotFound,
            deleteResult = false,
        )

        assertFailsWith<GroupNotFoundException> {
            UpdateGroupUseCase(port, RecordingGroupSubscribersPort())(
                UpdateGroupUseCase.Command(userId = 7, groupId = 17, name = "카페", color = "GREEN"),
            )
        }
        assertFailsWith<GroupNotFoundException> {
            DeleteGroupUseCase(port)(DeleteGroupUseCase.Command(userId = 7, groupId = 17))
        }
    }

    @Test
    fun `normalizes optional descriptions while preserving internal newlines`() {
        val create = CreateGroupUseCase(FakeGroupPort())
        val created = create(CreateGroupUseCase.Command(7, "카페", "YELLOW", "  카페\n취향 👨‍👩‍👧‍👦  "))
        assertEquals("카페\n취향 👨‍👩‍👧‍👦", created.description)
        for (description in listOf(null, "", " \n\t ")) {
            assertEquals(null, create(CreateGroupUseCase.Command(7, "카페", "YELLOW", description)).description)
        }
    }

    @Test
    fun `rejects oversized descriptions before either write`() {
        val description = "👨‍👩‍👧‍👦".repeat(151)
        assertFailsWith<InvalidGroupException> {
            CreateGroupUseCase(FakeGroupPort())(CreateGroupUseCase.Command(7, "카페", "YELLOW", description))
        }
        assertFailsWith<InvalidGroupException> {
            val update = UpdateGroupUseCase(FakeGroupPort(), RecordingGroupSubscribersPort())
            update(UpdateGroupUseCase.Command(7, 17, "카페", "YELLOW", description))
        }
    }

    private class FakeGroupPort(
        private val createResult: GroupView = GroupView(1, "카페", "YELLOW", 0),
        private val updateResult: GroupPort.UpdateResult =
            GroupPort.UpdateResult.Updated(GroupView(1, "카페", "GREEN", 3)),
        private val deleteResult: Boolean = true,
    ) : GroupPort {
        private var nextGroupId = createResult.id

        override fun findAll(userId: Long): List<GroupView> = emptyList()

        override fun findOwned(userId: Long): List<GroupView> = emptyList()

        override fun create(userId: Long, name: String, color: GroupColor, description: String?): GroupView =
            createResult.copy(id = nextGroupId++, name = name, color = color.name, description = description)

        override fun update(
            userId: Long,
            groupId: Long,
            name: String,
            color: GroupColor,
            description: String?,
        ): GroupPort.UpdateResult = updateResult

        override fun delete(userId: Long, groupId: Long): Boolean = deleteResult
    }
}
