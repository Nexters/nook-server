package org.every.nook.api.application.appversion

import kotlin.test.Test
import kotlin.test.assertEquals

class GetAppVersionPolicyUseCaseTest {
    private val policy = AppVersionPolicy(
        platform = AppPlatform.IOS,
        minimumSupportedBuildNumber = 10,
        latestBuildNumber = 20,
        latestVersion = "1.2.0",
        storeUrl = "https://example.com/app",
    )

    @Test
    fun `forces update below minimum supported build number`() {
        assertEquals(AppUpdateType.FORCE, execute(buildNumber = 9).updateType)
    }

    @Test
    fun `recommends update from minimum supported build through latest build exclusive`() {
        assertEquals(AppUpdateType.RECOMMEND, execute(buildNumber = 10).updateType)
        assertEquals(AppUpdateType.RECOMMEND, execute(buildNumber = 19).updateType)
    }

    @Test
    fun `does not require update at latest build or above`() {
        assertEquals(AppUpdateType.NONE, execute(buildNumber = 20).updateType)
        assertEquals(AppUpdateType.NONE, execute(buildNumber = 21).updateType)
    }

    @Test
    fun `fails open when platform policy is not registered`() {
        val result = GetAppVersionPolicyUseCase(AppVersionPolicyPort { null })(
            GetAppVersionPolicyUseCase.Query(AppPlatform.ANDROID, 7),
        )

        assertEquals(AppUpdateType.NONE, result.updateType)
        assertEquals(null, result.latestBuildNumber)
    }

    private fun execute(buildNumber: Long): AppVersionPolicyView = GetAppVersionPolicyUseCase(
        AppVersionPolicyPort { platform -> policy.takeIf { it.platform == platform } },
    )(GetAppVersionPolicyUseCase.Query(AppPlatform.IOS, buildNumber))
}
