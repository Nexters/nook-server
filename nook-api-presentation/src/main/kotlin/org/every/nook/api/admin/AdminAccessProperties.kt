package org.every.nook.api.admin

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.admin.access")
data class AdminAccessProperties(val enabled: Boolean = false, val teamDomain: String = "", val audience: String = "")
