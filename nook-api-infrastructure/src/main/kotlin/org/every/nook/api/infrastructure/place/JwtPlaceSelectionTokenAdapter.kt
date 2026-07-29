package org.every.nook.api.infrastructure.place

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSelectionTokenPort
import org.every.nook.api.infrastructure.auth.JwtProperties
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.util.Date

class JwtPlaceSelectionTokenAdapter(private val properties: JwtProperties, private val clock: Clock) :
    PlaceSelectionTokenPort {
    init {
        require(secretBytes().size >= MIN_SECRET_BYTES)
    }

    override fun issue(userId: Long, candidate: PlaceCandidate): String {
        val now = clock.instant()
        val claims = JWTClaimsSet.Builder()
            .issuer(properties.issuer)
            .subject(userId.toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(TOKEN_TTL)))
            .claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE)
            .claim(PROVIDER_CLAIM, candidate.provider)
            .claim(EXTERNAL_PLACE_ID_CLAIM, candidate.externalPlaceId)
            .claim(NAME_CLAIM, candidate.name)
            .claim(ADDRESS_CLAIM, candidate.address)
            .claim(LATITUDE_CLAIM, candidate.latitude.toPlainString())
            .claim(LONGITUDE_CLAIM, candidate.longitude.toPlainString())
            .claim(CATEGORY_CLAIM, candidate.category)
            .claim(PHONE_NUMBER_CLAIM, candidate.phoneNumber)
            .claim(PROVIDER_URL_CLAIM, candidate.providerUrl)
            .build()
        return SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims).apply {
            sign(MACSigner(secretBytes()))
        }.serialize()
    }

    override fun verify(userId: Long, token: String): PlaceCandidate? = runCatching {
        val jwt = SignedJWT.parse(token)
        val claims = jwt.jwtClaimsSet
        require(jwt.verify(MACVerifier(secretBytes())))
        require(claims.issuer == properties.issuer)
        require(claims.subject == userId.toString())
        require(claims.getStringClaim(TOKEN_TYPE_CLAIM) == TOKEN_TYPE)
        require(claims.expirationTime.toInstant().isAfter(clock.instant()))
        PlaceCandidate(
            provider = claims.getStringClaim(PROVIDER_CLAIM),
            externalPlaceId = claims.getStringClaim(EXTERNAL_PLACE_ID_CLAIM),
            name = claims.getStringClaim(NAME_CLAIM),
            address = claims.getStringClaim(ADDRESS_CLAIM),
            latitude = BigDecimal(claims.getStringClaim(LATITUDE_CLAIM)),
            longitude = BigDecimal(claims.getStringClaim(LONGITUDE_CLAIM)),
            category = claims.getStringClaim(CATEGORY_CLAIM),
            phoneNumber = claims.getStringClaim(PHONE_NUMBER_CLAIM),
            providerUrl = claims.getStringClaim(PROVIDER_URL_CLAIM),
        )
    }.getOrNull()

    private fun secretBytes(): ByteArray = properties.accessSecret.toByteArray(StandardCharsets.UTF_8)

    private companion object {
        const val MIN_SECRET_BYTES = 32
        val TOKEN_TTL: Duration = Duration.ofMinutes(10)
        const val TOKEN_TYPE_CLAIM = "token_type"
        const val TOKEN_TYPE = "place_selection"
        const val PROVIDER_CLAIM = "provider"
        const val EXTERNAL_PLACE_ID_CLAIM = "external_place_id"
        const val NAME_CLAIM = "name"
        const val ADDRESS_CLAIM = "address"
        const val LATITUDE_CLAIM = "latitude"
        const val LONGITUDE_CLAIM = "longitude"
        const val CATEGORY_CLAIM = "category"
        const val PHONE_NUMBER_CLAIM = "phone_number"
        const val PROVIDER_URL_CLAIM = "provider_url"
    }
}
