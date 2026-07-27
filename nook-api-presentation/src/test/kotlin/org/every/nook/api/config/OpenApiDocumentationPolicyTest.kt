package org.every.nook.api.config

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.group.GroupController
import org.every.nook.api.presentation.place.PlaceCandidateController
import org.every.nook.api.presentation.post.PostController
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import java.lang.reflect.Method
import kotlin.test.Test
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

    private fun Method.isApiHandler(): Boolean = apiMappingAnnotations.any(::isAnnotationPresent)

    private companion object {
        val controllers = listOf(
            GroupController::class.java,
            PlaceCandidateController::class.java,
            PostController::class.java,
        )
        val apiMappingAnnotations = listOf(
            GetMapping::class.java,
            PostMapping::class.java,
            PutMapping::class.java,
            PatchMapping::class.java,
            DeleteMapping::class.java,
        )
    }
}
