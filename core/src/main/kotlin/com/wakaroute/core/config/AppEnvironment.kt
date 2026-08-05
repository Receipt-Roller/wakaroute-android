package com.wakaroute.core.config

/**
 * Where the app points and who it says it is.
 *
 * Production and staging are separated by build type rather than by a runtime
 * flag, so a build cannot be pointed at the wrong backend by accident.
 *
 * No API key or client secret appears here. Device registration (Phase 2) is
 * the mechanism that replaces one; an embedded secret in an APK is extractable,
 * which is exactly the finding raised against another Android app in RR26-Dev.
 */
data class AppEnvironment(
    val wakarouteBaseUrl: String,
    val manabu2BaseUrl: String,
    val clientId: String,
    /**
     * The organization owning ワカルート's content.
     *
     * Every content read must be scoped to it. `GET /api/v1/paths` otherwise
     * also returns MANABU2's shared catalogue — corporate courses a 中学生 must
     * never see next to 数学. Unused in Phase 1, which touches no MANABU2
     * endpoint, but the scoping rule belongs with the environment rather than
     * being rediscovered in Phase 2.
     */
    val organizationId: String,
    /** Keystore alias namespace for Phase 2 credentials. */
    val keystoreService: String,
) {
    companion object {
        val Production = AppEnvironment(
            wakarouteBaseUrl = "https://wakaroute.com",
            manabu2BaseUrl = "https://api.manabu2.com",
            // Shared with iOS, deliberately. The 実装ガイド says `wakaroute-app`,
            // but `wakaroute` is the registration that actually exists and works:
            // device registration was enabled on it (LMS-DEV t-d1bea66) and its
            // OwnerOrganizationId is set (t-d1bea73). The guide's spelling is a
            // documentation error, not a second client.
            //
            // One client for both platforms costs an independent kill switch —
            // registration is permitted per client, so Android cannot be
            // disabled without disabling iOS. Accepted knowingly: `platform` is
            // already sent on every registration, the scopes are identical, and
            // a second client would need the same two server-side fixes that
            // once blocked this project outright.
            //
            // **Do not change this value once accounts exist.** It is not known
            // whether MANABU2 keys a learner by `deviceId` alone or by
            // `(clientId, deviceId)`. If it is the pair, changing it orphans
            // every Android student's records — three years of them.
            clientId = "wakaroute",
            organizationId = "a461577a-3410-4c98-b1d5-db729f3444a1",
            keystoreService = "com.wakaroute.app",
        )
    }
}
