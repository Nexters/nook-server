package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.PlaceCandidate
import org.springframework.stereotype.Component
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object PlaceIdentityRuleSpec {
    const val MIN_NAME_KEY_LENGTH = 3
    const val MAX_DISTANCE_METERS = 30.0
    const val ADJACENT_ADDRESS_MAX_DISTANCE_METERS = 10.0
    const val MAX_BUILDING_NUMBER_DIFFERENCE = 2
}

@Component
class PlaceIdentityMatcher {
    fun matches(existing: PlaceEntity, candidate: PlaceCandidate): Boolean {
        val distanceMeters = distanceMeters(existing, candidate)
        if (
            namesMatch(existing.name, candidate.name) &&
            addressesMatch(existing.address, candidate.address) &&
            distanceMeters <= MAX_DISTANCE_METERS
        ) {
            return true
        }
        return !existing.provider.equals(candidate.provider, ignoreCase = true) &&
            exactNamesMatch(existing.name, candidate.name) &&
            adjacentRoadAddressesMatch(existing.address, candidate.address) &&
            distanceMeters <= ADJACENT_ADDRESS_MAX_DISTANCE_METERS
    }

    internal fun namesMatch(left: String, right: String): Boolean {
        val leftKey = left.identityKey()
        val rightKey = right.identityKey()
        val shorter = minOf(leftKey, rightKey, compareBy(String::length))
        val longer = maxOf(leftKey, rightKey, compareBy(String::length))
        return shorter.length >= MIN_NAME_KEY_LENGTH && longer.contains(shorter)
    }

    internal fun addressesMatch(left: String, right: String): Boolean {
        val leftKey = left.addressKey() ?: return false
        val rightKey = right.addressKey() ?: return false
        return leftKey == rightKey
    }

    internal fun adjacentRoadAddressesMatch(left: String, right: String): Boolean {
        val leftAddress = left.roadAddress() ?: return false
        val rightAddress = right.roadAddress() ?: return false
        return leftAddress.roadKey == rightAddress.roadKey &&
            abs(leftAddress.mainBuildingNumber - rightAddress.mainBuildingNumber) <= MAX_BUILDING_NUMBER_DIFFERENCE
    }

    private fun exactNamesMatch(left: String, right: String): Boolean {
        val leftKey = left.identityKey()
        val rightKey = right.identityKey()
        return leftKey.length >= MIN_NAME_KEY_LENGTH && leftKey == rightKey
    }

    private fun distanceMeters(existing: PlaceEntity, candidate: PlaceCandidate): Double {
        val latitudeDelta = Math.toRadians(candidate.latitude.subtract(existing.latitude).toDouble())
        val longitudeDelta = Math.toRadians(candidate.longitude.subtract(existing.longitude).toDouble())
        val existingLatitude = Math.toRadians(existing.latitude.toDouble())
        val candidateLatitude = Math.toRadians(candidate.latitude.toDouble())
        val haversine = sin(latitudeDelta / 2).let { it * it } +
            cos(existingLatitude) * cos(candidateLatitude) * sin(longitudeDelta / 2).let { it * it }
        return EARTH_RADIUS_METERS * 2 * asin(sqrt(haversine))
    }

    private fun String.addressKey(): String? = roadAddress()?.let { address ->
        "${address.roadKey}${address.buildingNumber}"
    } ?: LOT_NUMBER_ADDRESS.find(this)?.let { match ->
        "${match.groupValues[1].identityKey()}${match.groupValues[2].identityKey()}" +
            match.groupValues[LOT_NUMBER_GROUP]
    }

    private fun String.roadAddress(): RoadAddress? = ROAD_ADDRESS.find(this)?.let { match ->
        val buildingNumber = match.groupValues[2]
        RoadAddress(
            roadKey = match.groupValues[1].identityKey(),
            buildingNumber = buildingNumber,
            mainBuildingNumber = buildingNumber.substringBefore('-').toInt(),
        )
    }

    private fun String.identityKey(): String = lowercase().filter(Char::isLetterOrDigit)

    private companion object {
        const val MIN_NAME_KEY_LENGTH = PlaceIdentityRuleSpec.MIN_NAME_KEY_LENGTH
        const val MAX_DISTANCE_METERS = PlaceIdentityRuleSpec.MAX_DISTANCE_METERS
        const val ADJACENT_ADDRESS_MAX_DISTANCE_METERS = PlaceIdentityRuleSpec.ADJACENT_ADDRESS_MAX_DISTANCE_METERS
        const val MAX_BUILDING_NUMBER_DIFFERENCE = PlaceIdentityRuleSpec.MAX_BUILDING_NUMBER_DIFFERENCE
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val LOT_NUMBER_GROUP = 3
        val ROAD_ADDRESS = Regex("([가-힣A-Za-z0-9·-]+(?:대로|로|길))\\s*(\\d+(?:-\\d+)?)")
        val LOT_NUMBER_ADDRESS = Regex("([가-힣A-Za-z0-9·-]+(?:동|읍|면|리))\\s*(산\\s*)?(\\d+(?:-\\d+)?)")
    }

    private data class RoadAddress(val roadKey: String, val buildingNumber: String, val mainBuildingNumber: Int)
}
