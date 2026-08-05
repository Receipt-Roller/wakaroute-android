package com.wakaroute.app

import android.app.Application
import com.wakaroute.app.data.KeystoreSecretStore
import com.wakaroute.app.data.TargetSchoolsState
import com.wakaroute.app.data.UnderstandingMapState
import com.wakaroute.core.auth.AuthSession
import com.wakaroute.core.auth.DeviceAuthClient
import com.wakaroute.core.auth.StoredDeviceIdProvider
import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.goals.HttpTargetSchoolsRepository
import com.wakaroute.core.map.BundledUnderstandingMapRepository
import com.wakaroute.core.map.LiveUnderstandingMap
import com.wakaroute.core.content.ContentClient
import com.wakaroute.core.net.AuthenticatedHttpClient
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.OkHttpHttpClient
import com.wakaroute.core.offline.FilePendingActionStore
import com.wakaroute.core.offline.LearningActionQueue
import java.io.File
import com.wakaroute.core.profile.ProfileClient
import com.wakaroute.core.study.StudyTimer
import com.wakaroute.core.schools.HttpSchoolsRepository
import com.wakaroute.core.schools.SchoolsRepository
import com.wakaroute.app.data.AppPreferences

/**
 * Wiring, assembled once at launch.
 *
 * Screens are handed what they need rather than reaching for singletons, which
 * is what keeps them previewable and their view models testable. No DI
 * framework: there are four dependencies, and a container would be more code
 * than the thing it contains.
 */
class WakaRouteApplication : Application() {

    lateinit var services: AppServices
        private set

    override fun onCreate() {
        super.onCreate()
        services = AppServices.live(this)
    }
}

class AppServices(
    val environment: AppEnvironment,
    val schools: SchoolsRepository,
    val understandingMap: UnderstandingMapState,
    val preferences: AppPreferences,
    /**
     * Phase 2's foundation, assembled but **not yet used by any screen**.
     *
     * Registration happens on the first call to `accessToken()`, and nothing
     * calls it today. That is deliberate: the app would otherwise create a
     * MANABU2 learner account for every install while there is still no
     * feature that writes anything to it. The account arrives with the screen
     * that needs it.
     */
    val auth: AuthSession,
    /** Use this for every MANABU2 call. It is the only path that renews safely. */
    val authenticatedHttp: HttpClient,
    val profile: ProfileClient,
    val content: ContentClient,
    val actionQueue: LearningActionQueue,
    val studyTimer: StudyTimer,
    val targetSchools: TargetSchoolsState,
) {
    companion object {
        fun live(application: Application): AppServices {
            val environment = AppEnvironment.Production
            val http = OkHttpHttpClient()
            val secrets = KeystoreSecretStore(application)

            val auth = AuthSession(
                client = DeviceAuthClient(http, environment),
                store = secrets,
                deviceIds = StoredDeviceIdProvider(secrets),
            )
            val authenticated = AuthenticatedHttpClient(http, auth)
            val content = ContentClient(authenticated, environment)
            val queue = LearningActionQueue(
                // Losing this file costs the records waiting in it, not the
                // feature — so it lives in filesDir rather than cache, which
                // the system may clear at any time.
                store = FilePendingActionStore(File(application.filesDir, "pending-actions.json")),
                content = content,
            )

            return AppServices(
                environment = environment,
                // Unauthenticated on purpose. The school catalogue needs no
                // token, and going through the authenticated client would make
                // browsing schools depend on registration having succeeded.
                schools = HttpSchoolsRepository(http, environment),
                // Phase 1 has no learner data of any kind, so the map is the
                // authored graph and an empty record. See
                // BundledUnderstandingMapRepository for why that is stated
                // rather than filled in.
                understandingMap = run {
                    val bundled = BundledUnderstandingMapRepository()
                    UnderstandingMapState(
                        bundled = bundled,
                        live = LiveUnderstandingMap(content, bundled),
                        isRegistered = auth::isRegistered,
                    )
                },
                content = content,
                actionQueue = queue,
                studyTimer = StudyTimer(queue),
                preferences = AppPreferences(application),
                auth = auth,
                authenticatedHttp = authenticated,
                profile = ProfileClient(authenticated, environment),
                targetSchools = TargetSchoolsState(
                    repository = HttpTargetSchoolsRepository(authenticated, environment),
                    isRegistered = auth::isRegistered,
                ),
            )
        }
    }
}
