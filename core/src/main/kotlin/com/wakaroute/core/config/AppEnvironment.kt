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
            // The 実装ガイド documents `wakaroute-app` while the iOS client
            // sends `wakaroute`. Phase 1 calls no authenticated endpoint, so
            // nothing here depends on it yet — but Phase 2 must not guess.
            // Raised as an AB question rather than settled by picking one.
            clientId = "wakaroute",
            organizationId = "a461577a-3410-4c98-b1d5-db729f3444a1",
            keystoreService = "com.wakaroute.app",
        )
    }
}
