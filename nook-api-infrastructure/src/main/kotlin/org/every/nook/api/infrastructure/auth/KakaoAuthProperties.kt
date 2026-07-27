package org.every.nook.api.infrastructure.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("auth.kakao")
data class KakaoAuthProperties(var appId: Long? = null)
