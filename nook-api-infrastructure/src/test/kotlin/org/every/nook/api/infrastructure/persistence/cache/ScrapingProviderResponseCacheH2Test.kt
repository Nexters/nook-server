package org.every.nook.api.infrastructure.persistence.cache

import jakarta.persistence.EntityManagerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.annotation.EnableTransactionManagement
import javax.sql.DataSource
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringJUnitConfig(ScrapingProviderResponseCacheH2Test.Config::class)
class ScrapingProviderResponseCacheH2Test {
    @Autowired
    private lateinit var repository: ScrapingProviderResponseJpaRepository

    @Autowired
    private lateinit var dataSource: DataSource

    @BeforeTest
    fun clear() {
        repository.deleteAll()
    }

    @Test
    fun `eviction removes only rejected response and permits fresh cache replacement`() {
        insert("BRIGHT_DATA", "[{}]")
        insert("APIFY", "apify-response")
        val cache = ScrapingProviderResponseCache(repository)
        cache.evict("BRIGHT_DATA", "INSTAGRAM", "Post123", "[{}]")
        assertNull(cache.find("BRIGHT_DATA", "INSTAGRAM", "Post123"))
        assertEquals("apify-response", cache.find("APIFY", "INSTAGRAM", "Post123"))
        cache.save("BRIGHT_DATA", "INSTAGRAM", "Post123", "recovered")
        assertEquals("recovered", cache.find("BRIGHT_DATA", "INSTAGRAM", "Post123"))
    }

    @Test
    fun `stale eviction cannot remove a response already replaced by another request`() {
        insert("BRIGHT_DATA", "recovered")
        val cache = ScrapingProviderResponseCache(repository)
        cache.evict("BRIGHT_DATA", "INSTAGRAM", "Post123", "[{}]")
        assertEquals("recovered", cache.find("BRIGHT_DATA", "INSTAGRAM", "Post123"))
    }

    private fun insert(provider: String, body: String) {
        JdbcTemplate(dataSource).update(
            "INSERT INTO scraping_provider_responses " +
                "(provider, source_type, external_post_id, response_body, created_at, updated_at) " +
                "VALUES (?, 'INSTAGRAM', 'Post123', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            provider,
            body,
        )
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(basePackageClasses = [ScrapingProviderResponseJpaRepository::class])
    class Config {
        @Bean
        fun dataSource(): DataSource = DriverManagerDataSource(
            "jdbc:h2:mem:scraping-cache;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
