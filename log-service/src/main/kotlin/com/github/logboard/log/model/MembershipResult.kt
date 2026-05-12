package com.github.logboard.log.model

sealed class MembershipResult {
    data class Found(val role: String) : MembershipResult()
    object NotMember : MembershipResult()
    object Unavailable : MembershipResult()
}
