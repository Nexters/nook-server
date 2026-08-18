package org.every.nook.api.application.admin

data class AdminActor(val subject: String, val email: String) {
    init {
        require(subject.isNotBlank()) { "Admin actor subject must not be blank" }
        require(email.isNotBlank()) { "Admin actor email must not be blank" }
    }
}
