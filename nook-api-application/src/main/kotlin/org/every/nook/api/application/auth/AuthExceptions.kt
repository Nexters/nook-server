package org.every.nook.api.application.auth

class InvalidSocialCredentialException : RuntimeException("Invalid social credential")

class InvalidSignupTokenException : RuntimeException("Invalid signup token")

class InvalidRefreshTokenException : RuntimeException("Invalid refresh token")

class ReusedRefreshTokenException : RuntimeException("Refresh token was already used")
