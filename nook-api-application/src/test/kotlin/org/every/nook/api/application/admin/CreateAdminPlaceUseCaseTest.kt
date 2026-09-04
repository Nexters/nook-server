package org.every.nook.api.application.admin

import org.every.nook.api.domain.place.PlaceTag
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CreateAdminPlaceUseCaseTest {
    @Test
    fun `resolves and normalizes address before creating a manual place`() {
        var captured: AdminPlaceCreationPort.CreateCommand? = null
        val useCase = CreateAdminPlaceUseCase(
            addressResolver = AdminPlaceAddressResolver { address ->
                assertEquals("서울 성동구 성수동 1", address)
                AdminPlaceAddressResolver.ResolvedAddress(
                    address = "서울특별시 성동구 아차산로 1",
                    latitude = BigDecimal("37.5120741"),
                    longitude = BigDecimal("127.0590297"),
                )
            },
            creationPort = AdminPlaceCreationPort { command ->
                captured = command
                detail(command)
            },
        )

        val result = useCase(
            CreateAdminPlaceUseCase.Command(
                name = "  누크 카페  ",
                address = "  서울 성동구 성수동 1  ",
                category = "  카페  ",
                actor = AdminActor("subject", "admin@everynook.co.kr"),
                reason = "  신규 등록  ",
                requestId = "request-1",
            ),
        )

        assertEquals("MANUAL", captured?.provider)
        assertEquals("누크 카페", captured?.name)
        assertEquals("서울특별시 성동구 아차산로 1", captured?.address)
        assertEquals("서울", captured?.city)
        assertEquals("카페", captured?.category)
        assertEquals("신규 등록", captured?.reason)
        assertEquals(64, captured?.externalPlaceId?.length)
        assertEquals("서울특별시 성동구 아차산로 1", result.address)
    }

    @Test
    fun `uses a stable manual identity for equivalent input spacing`() {
        val identities = mutableListOf<String>()
        val useCase = fixture { command -> identities += command.externalPlaceId }

        useCase(command(name = "누크 카페", address = "서울 성동구 성수동 1"))
        useCase(command(name = "누크카페", address = "서울성동구 성수동1"))

        assertEquals(identities.first(), identities.last())
    }

    @Test
    fun `rejects an address that cannot be resolved exactly`() {
        val useCase = CreateAdminPlaceUseCase(
            addressResolver = AdminPlaceAddressResolver { null },
            creationPort = AdminPlaceCreationPort { error("must not create") },
        )

        assertFailsWith<AdminPlaceAddressNotFoundException> { useCase(command()) }
    }

    private fun fixture(onCreate: (AdminPlaceCreationPort.CreateCommand) -> Unit): CreateAdminPlaceUseCase =
        CreateAdminPlaceUseCase(
            addressResolver = AdminPlaceAddressResolver {
                AdminPlaceAddressResolver.ResolvedAddress(
                    "서울특별시 성동구 아차산로 1",
                    BigDecimal("37.5120741"),
                    BigDecimal("127.0590297"),
                )
            },
            creationPort = AdminPlaceCreationPort { command ->
                onCreate(command)
                detail(command)
            },
            tagCatalogPort = { PlaceTag.defaultDefinitions },
        )

    private fun command(): CreateAdminPlaceUseCase.Command = command("누크 카페", "서울 성동구 성수동 1")

    private fun command(name: String, address: String) = CreateAdminPlaceUseCase.Command(
        name = name,
        address = address,
        actor = AdminActor("subject", "admin@everynook.co.kr"),
        reason = "신규 등록",
        requestId = null,
    )

    private fun detail(command: AdminPlaceCreationPort.CreateCommand) = AdminPlaceDetail(
        id = 1,
        name = command.name,
        address = command.address,
        provider = command.provider,
        externalPlaceId = command.externalPlaceId,
        latitude = command.latitude.toPlainString(),
        longitude = command.longitude.toPlainString(),
    )
}
