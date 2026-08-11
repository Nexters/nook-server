package org.every.nook.api.logging

import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.type.classreading.CachingMetadataReaderFactory
import java.util.Locale
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

class PrivacyArgumentFieldNames(private val names: Set<String>) {
    fun contains(name: String): Boolean = name.toLogFieldName() in names

    companion object {
        fun scan(basePackage: String): PrivacyArgumentFieldNames {
            val classLoader = Thread.currentThread().contextClassLoader
            val resolver = PathMatchingResourcePatternResolver(classLoader)
            val readerFactory = CachingMetadataReaderFactory(resolver)
            val packagePath = basePackage.replace('.', '/')
            val resources = resolver.getResources("classpath*:$packagePath/**/*.class")
            val names = resources.flatMap { resource ->
                val className = readerFactory.getMetadataReader(resource).classMetadata.className
                runCatching { Class.forName(className, false, classLoader).kotlin }
                    .getOrNull()
                    ?.memberProperties
                    ?.filter { property ->
                        property.findAnnotation<PrivacyArgument>() != null ||
                            property.javaField?.getAnnotation(PrivacyArgument::class.java) != null
                    }
                    ?.map { property -> property.name.toLogFieldName() }
                    .orEmpty()
            }.toSet()
            return PrivacyArgumentFieldNames(names)
        }
    }
}

private fun String.toLogFieldName(): String = lowercase(Locale.ROOT)
    .replace('-', '_')
    .replace(Regex("[^a-z0-9_.]"), "_")
    .replace(Regex("_+"), "_")
    .trim('_')
