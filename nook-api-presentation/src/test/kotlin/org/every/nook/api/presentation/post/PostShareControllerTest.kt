package org.every.nook.api.presentation.post

import org.every.nook.api.application.post.share.GetPostShareUseCase
import org.every.nook.api.application.post.share.IssuePostShareUseCase
import org.every.nook.api.application.post.share.PostShareError
import org.every.nook.api.application.post.share.PostShareException
import org.every.nook.api.application.post.share.PostShareLink
import org.every.nook.api.application.post.share.PostSharePort
import org.every.nook.api.application.post.share.PostShareSnapshot
import org.every.nook.api.application.post.share.PostShareView
import org.every.nook.api.application.post.share.SavePostShareResult
import org.every.nook.api.application.post.share.SavePostShareUseCase
import org.every.nook.api.presentation.auth.UserContextArgumentResolver
import org.every.nook.api.presentation.error.GlobalExceptionHandler
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PostShareControllerTest {
    private val port = RecordingPort()
    private val mvc = MockMvcBuilders.standaloneSetup(
        PostShareController(IssuePostShareUseCase(port), SavePostShareUseCase(port)),
        PublicPostShareController(GetPostShareUseCase(port)),
    ).setCustomArgumentResolvers(UserContextArgumentResolver())
        .setControllerAdvice(GlobalExceptionHandler()).build()

    @AfterTest
    fun cleanup() = SecurityContextHolder.clearContext()

    @Test
    fun `public snapshot does not expose private fields and is accessible without login`() {
        mvc.get("/api/public/v1/post-shares/token").andExpect {
            status { isOk() }
            jsonPath("$.success.post.canonicalUrl") { value("https://instagram.test/p/ABC/") }
            jsonPath("$.success.post.memo") { doesNotExist() }
            jsonPath("$.success.post.groups") { doesNotExist() }
            jsonPath("$.success.viewerSavedPostId") { doesNotExist() }
            jsonPath("$.success.sharedAt") { value("2026-10-18T09:00:00+09:00") }
        }
        assertEquals(null, port.viewerId)
    }

    @Test
    fun `authenticated viewer receives their own saved identity`() {
        mvc.get("/api/public/v1/post-shares/token") {
            principal = TestingAuthenticationToken("8", "", "ROLE_USER")
        }.andExpect {
            status { isOk() }
            jsonPath("$.success.viewerSavedPostId") { value(22) }
        }
        assertEquals(8, port.viewerId)
    }

    @Test
    fun `issuing and saving use authenticated user and deduplicate archives`() {
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("8", "", "ROLE_USER")
        mvc.post("/api/v1/posts/11/share-links").andExpect {
            status { isOk() }
            jsonPath("$.success.token") { value("issued-token") }
        }
        assertEquals(listOf(8L, 11L), port.issued)
        mvc.post("/api/v1/post-shares/token/save") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"groupIds":[9,9]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.success.postId") { value(22) }
            jsonPath("$.success.alreadySaved") { value(true) }
        }
        assertEquals(setOf(9L), port.groups)
        assertEquals(8, port.savedBy)
    }

    @Test
    fun `save requires login and a nonempty owned archive selection`() {
        mvc.post("/api/v1/post-shares/token/save") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"groupIds":[9]}"""
        }.andExpect { status { isUnauthorized() } }
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("8", "", "ROLE_USER")
        mvc.post("/api/v1/post-shares/token/save") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"groupIds":[]}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `expired links return gone rather than stale public content`() {
        port.expired = true
        mvc.get("/api/public/v1/post-shares/token").andExpect {
            status { isGone() }
            jsonPath("$.error.errorCode") { value("POST_SHARE_EXPIRED") }
        }
    }

    private class RecordingPort : PostSharePort {
        var viewerId: Long? = null
        var issued: List<Long> = emptyList()
        var savedBy: Long? = null
        var groups = emptySet<Long>()
        var expired = false
        private val now = Instant.parse("2026-10-18T00:00:00Z")
        override fun issue(ownerId: Long, savedPostId: Long): PostShareLink {
            issued = listOf(ownerId, savedPostId)
            return PostShareLink("issued-token", now)
        }
        override fun find(token: String, viewerId: Long?): PostShareView {
            if (expired) throw PostShareException(PostShareError.EXPIRED)
            this.viewerId = viewerId
            return PostShareView(
                PostShareSnapshot(
                    null,
                    null,
                    null,
                    "https://instagram.test/p/ABC/",
                    null,
                    emptyList(),
                    emptyList(),
                    emptyList(),
                ),
                now,
                viewerId?.let { 22L },
            )
        }
        override fun save(userId: Long, token: String, groupIds: Set<Long>): SavePostShareResult {
            savedBy = userId
            groups = groupIds
            return SavePostShareResult(22, true)
        }
    }
}
