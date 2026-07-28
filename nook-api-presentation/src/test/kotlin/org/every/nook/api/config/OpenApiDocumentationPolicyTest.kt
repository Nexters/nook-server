package org.every.nook.api.config

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.every.nook.api.auth.AuthController
import org.every.nook.api.auth.RefreshTokenRequest
import org.every.nook.api.auth.SocialAuthRequest
import org.every.nook.api.auth.SocialAuthResponse
import org.every.nook.api.auth.TokenResponse
import org.every.nook.api.member.MemberController
import org.every.nook.api.member.SignupMemberRequest
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.group.GroupController
import org.every.nook.api.presentation.group.request.CreateGroupRequest
import org.every.nook.api.presentation.group.request.UpdateGroupRequest
import org.every.nook.api.presentation.group.response.GroupResponse
import org.every.nook.api.presentation.place.PlaceController
import org.every.nook.api.presentation.place.request.UpdatePlaceBookmarkRequest
import org.every.nook.api.presentation.place.response.PlaceDetailResponse
import org.every.nook.api.presentation.place.response.PlacePostMediaResponse
import org.every.nook.api.presentation.place.response.PlacePostPageResponse
import org.every.nook.api.presentation.place.response.PlacePostResponse
import org.every.nook.api.presentation.post.PostController
import org.every.nook.api.presentation.post.request.CreatePostRequest
import org.every.nook.api.presentation.post.request.ReplaceSavedPostGroupsRequest
import org.every.nook.api.presentation.post.request.UpdatePostMemoRequest
import org.every.nook.api.presentation.post.response.PlaceResponse
import org.every.nook.api.presentation.post.response.PostPlaceParsingResponse
import org.every.nook.api.presentation.post.response.PostResponse
import org.every.nook.api.presentation.post.response.SavedPostDetailResponse
import org.every.nook.api.presentation.post.response.SavedPostMediaResponse
import org.every.nook.api.presentation.post.response.SavedPostPageResponse
import org.every.nook.api.presentation.post.response.SavedPostPlaceResponse
import org.every.nook.api.presentation.post.response.SavedPostSummaryResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenApiDocumentationPolicyTest {
    @Test
    fun `public controllers declare an OpenAPI tag`() {
        controllers.forEach { controller ->
            assertNotNull(controller.getAnnotation(Tag::class.java), controller.simpleName)
        }
    }

    @Test
    fun `public API handlers declare an OpenAPI operation`() {
        controllers
            .flatMap { it.declaredMethods.toList() }
            .filter { it.isApiHandler() }
            .forEach { method ->
                assertNotNull(method.getAnnotation(Operation::class.java), method.toString())
            }
    }

    @Test
    fun `UserContext parameters are hidden from OpenAPI`() {
        val userContextParameters = controllers
            .flatMap { it.declaredMethods.toList() }
            .filter { it.isApiHandler() }
            .flatMap { it.parameters.toList() }
            .filter { it.type == UserContext::class.java }

        assertTrue(userContextParameters.isNotEmpty())
        userContextParameters.forEach { parameter ->
            assertTrue(parameter.getAnnotation(Parameter::class.java)?.hidden == true)
        }
    }

    @Test
    fun `validation-only request properties are hidden from OpenAPI schemas`() {
        val requestTypes = listOf(
            CreatePostRequest::class.java,
            ReplaceSavedPostGroupsRequest::class.java,
        )

        requestTypes.forEach { requestType ->
            val schemas = ModelConverters.getInstance().readAll(requestType)

            schemas.values.forEach { schema ->
                assertFalse(
                    schema.properties.orEmpty().containsKey("areGroupIdsPositive"),
                    requestType.simpleName.orEmpty(),
                )
            }
        }
    }

    @Test
    fun `public request and response schema properties declare descriptions`() {
        schemaTypes.forEach { schemaType ->
            schemaType.declaredFields
                .filter { it.isOpenApiProperty() }
                .forEach { field ->
                    assertFalse(
                        field.getAnnotation(io.swagger.v3.oas.annotations.media.Schema::class.java)
                            ?.description
                            .isNullOrBlank(),
                        "${schemaType.simpleName}.${field.name}",
                    )
                }
        }
    }

    @Test
    fun `public API parameters declare descriptions unless hidden`() {
        controllers
            .flatMap { it.declaredMethods.toList() }
            .filter { it.isApiHandler() }
            .flatMap { it.parameters.toList() }
            .filter { it.isDocumentedApiParameter() }
            .forEach { parameter ->
                val annotation = parameter.getAnnotation(Parameter::class.java)

                if (annotation?.hidden == true) {
                    assertTrue(annotation.description.isBlank(), parameter.toString())
                } else {
                    assertFalse(
                        annotation?.description.isNullOrBlank(),
                        parameter.toString(),
                    )
                }
            }
    }

    @Test
    fun `authentication entry points do not require an access token in OpenAPI`() {
        listOf(AuthController::class.java, MemberController::class.java)
            .flatMap { it.declaredMethods.toList() }
            .filter { it.isApiHandler() }
            .forEach { method ->
                assertTrue(method.getAnnotation(Operation::class.java).security.isEmpty(), method.toString())
            }
    }

    private fun Method.isApiHandler(): Boolean = apiMappingAnnotations.any(::isAnnotationPresent)

    private fun Field.isOpenApiProperty(): Boolean = !isSynthetic &&
        !Modifier.isStatic(modifiers) &&
        name != "Companion" &&
        getAnnotation(io.swagger.v3.oas.annotations.media.Schema::class.java)?.hidden != true

    private fun java.lang.reflect.Parameter.isDocumentedApiParameter(): Boolean =
        isAnnotationPresent(PathVariable::class.java) ||
            isAnnotationPresent(RequestParam::class.java) ||
            isAnnotationPresent(RequestHeader::class.java) ||
            getAnnotation(Parameter::class.java)?.hidden == true

    private companion object {
        val controllers = listOf(
            AuthController::class.java,
            GroupController::class.java,
            MemberController::class.java,
            PlaceController::class.java,
            PostController::class.java,
        )
        val apiMappingAnnotations = listOf(
            GetMapping::class.java,
            PostMapping::class.java,
            PutMapping::class.java,
            PatchMapping::class.java,
            DeleteMapping::class.java,
        )
        val schemaTypes = listOf(
            CreateGroupRequest::class.java,
            UpdateGroupRequest::class.java,
            GroupResponse::class.java,
            UpdatePlaceBookmarkRequest::class.java,
            PlaceDetailResponse::class.java,
            PlacePostPageResponse::class.java,
            PlacePostResponse::class.java,
            PlacePostMediaResponse::class.java,
            CreatePostRequest::class.java,
            ReplaceSavedPostGroupsRequest::class.java,
            UpdatePostMemoRequest::class.java,
            PostResponse::class.java,
            PostPlaceParsingResponse::class.java,
            PlaceResponse::class.java,
            SavedPostPageResponse::class.java,
            SavedPostSummaryResponse::class.java,
            SavedPostDetailResponse::class.java,
            SavedPostMediaResponse::class.java,
            SavedPostPlaceResponse::class.java,
            SocialAuthRequest::class.java,
            RefreshTokenRequest::class.java,
            SocialAuthResponse::class.java,
            TokenResponse::class.java,
            SignupMemberRequest::class.java,
        )
    }
}
