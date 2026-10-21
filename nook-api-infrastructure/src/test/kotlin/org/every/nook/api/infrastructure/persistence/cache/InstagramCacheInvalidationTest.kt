package org.every.nook.api.infrastructure.persistence.cache

import jakarta.persistence.EntityManagerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringJUnitConfig(InstagramCacheInvalidationTest.Config::class)
@Transactional
class InstagramCacheInvalidationTest {
    @Autowired
    private lateinit var repository: ScrapingProviderResponseJpaRepository

    @Test
    fun `invalidates both Instagram providers only for the target shortcode and permits new response`() {
        listOf(
            ScrapingProviderResponseEntity("APIFY", "INSTAGRAM", "ABC123", "old"),
            ScrapingProviderResponseEntity("BRIGHT_DATA", "INSTAGRAM", "ABC123", "old"),
            ScrapingProviderResponseEntity("APIFY", "INSTAGRAM", "OTHER", "keep"),
            ScrapingProviderResponseEntity("APIFY", "NAVER", "ABC123", "keep"),
        ).forEach(repository::saveAndFlush)
        assertEquals(2, repository.deleteInstagramResponses("ABC123"))
        assertNull(repository.findByProviderAndSourceTypeAndExternalPostId("APIFY", "INSTAGRAM", "ABC123"))
        assertNull(repository.findByProviderAndSourceTypeAndExternalPostId("BRIGHT_DATA", "INSTAGRAM", "ABC123"))
        assertNotNull(repository.findByProviderAndSourceTypeAndExternalPostId("APIFY", "INSTAGRAM", "OTHER"))
        assertNotNull(repository.findByProviderAndSourceTypeAndExternalPostId("APIFY", "NAVER", "ABC123"))
        repository.saveAndFlush(ScrapingProviderResponseEntity("APIFY", "INSTAGRAM", "ABC123", "new"))
        assertEquals(
            "new",
            repository.findByProviderAndSourceTypeAndExternalPostId("APIFY", "INSTAGRAM", "ABC123")?.responseBody,
        )
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(basePackageClasses = [ScrapingProviderResponseJpaRepository::class])
    class Config {
        @Bean
        fun dataSource(): DataSource = DriverManagerDataSource(
            "jdbc:h2:mem:instagram-invalidation;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "sa",
            "",
        )

        @Bean
        fun entityManagerFactory(dataSource: DataSource) = LocalContainerEntityManagerFactoryBean().apply {
            setDataSource(dataSource)
            setPackagesToScan(ScrapingProviderResponseEntity::class.java.packageName)
            jpaVendorAdapter = HibernateJpaVendorAdapter()
            setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "create-drop"))
        }

        @Bean
        fun transactionManager(factory: EntityManagerFactory) = JpaTransactionManager(factory)
    }
}
