package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.member.port.ProfileImageUploadPort
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.infrastructure.persistence.cache.MediaUrlCacheJpaRepository
import org.every.nook.api.infrastructure.storage.JdkRemoteMediaDownloader
import org.every.nook.api.infrastructure.storage.JdkRemoteMediaHttpClient
import org.every.nook.api.infrastructure.storage.MediaObjectStorage
import org.every.nook.api.infrastructure.storage.MediaStorageProperties
import org.every.nook.api.infrastructure.storage.PublicHttpsUriValidator
import org.every.nook.api.infrastructure.storage.RemoteMediaDownloader
import org.every.nook.api.infrastructure.storage.RemoteMediaHttpClient
import org.every.nook.api.infrastructure.storage.S3MediaObjectStorage
import org.every.nook.api.infrastructure.storage.S3PostMediaStorageAdapter
import org.every.nook.api.infrastructure.storage.S3ProfileImageUploadAdapter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.http.HttpClient
import java.time.Clock

@Configuration
@ConditionalOnProperty(prefix = "external.media-storage", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(MediaStorageProperties::class)
class MediaStorageConfig {
    @Bean
    fun mediaS3Client(properties: MediaStorageProperties): S3Client = S3Client.builder()
        .region(Region.of(properties.region))
        .credentialsProvider(DefaultCredentialsProvider.builder().build())
        .httpClientBuilder(UrlConnectionHttpClient.builder())
        .build()

    @Bean
    fun mediaS3Presigner(properties: MediaStorageProperties): S3Presigner = S3Presigner.builder()
        .region(Region.of(properties.region))
        .credentialsProvider(DefaultCredentialsProvider.builder().build())
        .build()

    @Bean
    fun mediaHttpClient(properties: MediaStorageProperties): HttpClient = HttpClient.newBuilder()
        .connectTimeout(properties.connectTimeout)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    @Bean
    fun publicHttpsUriValidator(): PublicHttpsUriValidator = PublicHttpsUriValidator()

    @Bean
    fun remoteMediaHttpClient(mediaHttpClient: HttpClient, properties: MediaStorageProperties): RemoteMediaHttpClient =
        JdkRemoteMediaHttpClient(mediaHttpClient, properties)

    @Bean
    fun remoteMediaDownloader(
        remoteMediaHttpClient: RemoteMediaHttpClient,
        properties: MediaStorageProperties,
        uriValidator: PublicHttpsUriValidator,
    ): RemoteMediaDownloader = JdkRemoteMediaDownloader(remoteMediaHttpClient, properties, uriValidator)

    @Bean
    fun mediaObjectStorage(mediaS3Client: S3Client, properties: MediaStorageProperties): MediaObjectStorage =
        S3MediaObjectStorage(mediaS3Client, properties)

    @Bean
    fun s3PostMediaStorageAdapter(
        downloader: RemoteMediaDownloader,
        objectStorage: MediaObjectStorage,
        properties: MediaStorageProperties,
        cacheRepository: MediaUrlCacheJpaRepository,
    ): PostMediaStoragePort = S3PostMediaStorageAdapter(downloader, objectStorage, properties, cacheRepository)

    @Bean
    fun s3ProfileImageUploadAdapter(
        mediaS3Presigner: S3Presigner,
        properties: MediaStorageProperties,
        clock: Clock,
    ): ProfileImageUploadPort = S3ProfileImageUploadAdapter(mediaS3Presigner, properties, clock)
}
