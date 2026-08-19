package org.every.nook.api.infrastructure.billing

import org.every.nook.api.infrastructure.persistence.billing.ExternalApiPricePolicyEntity
import org.every.nook.api.infrastructure.persistence.billing.ExternalApiPricePolicyJpaRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.math.RoundingMode

class OfficialExternalApiPricingInitializer(
    private val properties: OfficialExternalApiPricingProperties,
    private val repository: ExternalApiPricePolicyJpaRepository,
    private val transactionTemplate: TransactionTemplate,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        if (!properties.enabled) return
        transactionTemplate.executeWithoutResult {
            officialPrices(properties.usdKrwRate).forEach(::upsertManagedPrice)
        }
    }

    private fun upsertManagedPrice(price: OfficialPrice) {
        val existing = repository.findByProviderAndSku(price.provider, price.sku)
        if (existing != null && !existing.managed) return
        val policy = existing ?: ExternalApiPricePolicyEntity(
            provider = price.provider,
            sku = price.sku,
            unitPriceKrw = price.unitPriceKrw,
            unitSize = price.unitSize,
        )
        policy.unitPriceKrw = price.unitPriceKrw
        policy.unitSize = price.unitSize
        policy.freeMonthlyUnits = price.freeMonthlyUnits
        policy.sourceUrl = price.sourceUrl
        policy.sourceCurrency = price.sourceCurrency
        policy.sourceUnitPrice = price.sourceUnitPrice
        policy.managed = true
        policy.enabled = true
        repository.save(policy)
    }

    private fun officialPrices(usdKrwRate: BigDecimal): List<OfficialPrice> = listOf(
        usd("google-places", "nearby-search", "32", "5000", usdKrwRate, GOOGLE_MAPS_PRICING),
        usd("google-places", "text-search-pro", "32", "5000", usdKrwRate, GOOGLE_MAPS_PRICING),
        usd("google-places", "place-details", "20", "1000", usdKrwRate, GOOGLE_MAPS_PRICING),
        usd("google-places", "place-photo", "7", "1000", usdKrwRate, GOOGLE_MAPS_PRICING),
        usd("google-cloud-vision", "DOCUMENT_TEXT_DETECTION", "1.5", "1000", usdKrwRate, VISION_PRICING),
        krw("kakao-local", "keyword-search", "2", "3000000", KAKAO_PRICING),
        usd("bright-data", "dataset-scrape", "1.5", "5000", usdKrwRate, BRIGHT_DATA_PRICING),
        usd("openai", "gpt-5-nano", "0.4", "0", usdKrwRate, OPENAI_PRICING, MILLION),
        usd("openai", "gpt-5-nano-input", "0.05", "0", usdKrwRate, OPENAI_PRICING, MILLION),
        usd("openai", "gpt-5-nano-cached-input", "0.005", "0", usdKrwRate, OPENAI_PRICING, MILLION),
        usd("openai", "gpt-5-nano-output", "0.4", "0", usdKrwRate, OPENAI_PRICING, MILLION),
    )

    private fun usd(
        provider: String,
        sku: String,
        pricePerThousand: String,
        freeMonthlyUnits: String,
        usdKrwRate: BigDecimal,
        sourceUrl: String,
        unitSize: BigDecimal = THOUSAND,
    ): OfficialPrice {
        val sourcePrice = BigDecimal(pricePerThousand)
        return OfficialPrice(
            provider,
            sku,
            sourcePrice.multiply(usdKrwRate).setScale(COST_SCALE, RoundingMode.HALF_UP),
            unitSize,
            BigDecimal(freeMonthlyUnits),
            sourceUrl,
            "USD",
            sourcePrice,
        )
    }

    private fun krw(
        provider: String,
        sku: String,
        pricePerCall: String,
        freeMonthlyUnits: String,
        sourceUrl: String,
    ): OfficialPrice = OfficialPrice(
        provider,
        sku,
        BigDecimal(pricePerCall),
        BigDecimal.ONE,
        BigDecimal(freeMonthlyUnits),
        sourceUrl,
        "KRW",
        BigDecimal(pricePerCall),
    )

    private data class OfficialPrice(
        val provider: String,
        val sku: String,
        val unitPriceKrw: BigDecimal,
        val unitSize: BigDecimal,
        val freeMonthlyUnits: BigDecimal,
        val sourceUrl: String,
        val sourceCurrency: String,
        val sourceUnitPrice: BigDecimal,
    )

    private companion object {
        val THOUSAND = BigDecimal(1000)
        val MILLION = BigDecimal(1_000_000)
        const val COST_SCALE = 6
        const val GOOGLE_MAPS_PRICING = "https://developers.google.com/maps/billing-and-pricing/pricing"
        const val VISION_PRICING = "https://cloud.google.com/vision/pricing"
        const val KAKAO_PRICING = "https://developers.kakao.com/docs/latest/en/getting-started/quota"
        const val BRIGHT_DATA_PRICING = "https://brightdata.com/pricing/web-scraper"
        const val OPENAI_PRICING = "https://developers.openai.com/api/docs/models/gpt-5-nano"
    }
}
