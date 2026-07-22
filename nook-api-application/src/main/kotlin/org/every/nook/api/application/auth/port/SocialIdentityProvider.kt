package org.every.nook.api.application.auth.port

import org.every.nook.api.application.auth.SocialCredential
import org.every.nook.api.application.auth.SocialIdentity

interface SocialIdentityProvider {
    fun authenticate(credential: SocialCredential): SocialIdentity
}
