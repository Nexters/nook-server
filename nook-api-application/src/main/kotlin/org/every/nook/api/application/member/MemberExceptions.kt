package org.every.nook.api.application.member

class DuplicateNicknameException(cause: Throwable? = null) : RuntimeException("Nickname is already in use", cause)

class DuplicateSocialAccountException(cause: Throwable? = null) :
    RuntimeException("Social account is already registered", cause)

class MemberNotFoundException : RuntimeException("Member was not found")
