package com.wakaroute.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.wakaroute.app.data.KeystoreSecretStore
import com.wakaroute.app.data.TargetSchoolsState
import com.wakaroute.app.data.UnderstandingMapState
import com.wakaroute.core.auth.AccountDeletion
import com.wakaroute.core.auth.AccountHandover
import com.wakaroute.core.auth.AuthSession
import com.wakaroute.core.auth.DeviceAuthClient
import com.wakaroute.core.auth.StoredDeviceIdProvider
import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.goals.HttpTargetSchoolsRepository
import com.wakaroute.core.map.FilePrerequisiteGraphStore
import com.wakaroute.core.map.GraphUnderstandingMapRepository
import com.wakaroute.core.map.PrerequisiteGraphClient
import com.wakaroute.core.map.PrerequisiteGraphSync
import com.wakaroute.core.map.PublishedGraphCatalogue
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
        registerInBackground()
    }

    /**
     * Creates the learner account at launch, as iOS does.
     *
     * Every screen that shows a student's own work needs a token, and the
     * content endpoints are authenticated — `GET /courses/{id}` is a 401
     * without one. Registering lazily meant a student who opened a lesson
     * before registering a 志望校 was told 「レッスンはまだ読み込んでいません」,
     * with nothing on screen to tell them why or what to do. 志望校 as the
     * trigger was arbitrary; the two apps now behave the same way.
     *
     * Fire-and-forget on purpose. A first launch with no signal must not block
     * or crash — [AuthSession] registers again on the next call that needs a
     * token, so the only cost of failing here is that the first screen to want
     * learner data pays for it.
     */
    private fun registerInBackground() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { services.auth.accessToken() }
        }
    }
}

class AppServices(
    val environment: AppEnvironment,
    val schools: SchoolsRepository,
    val understandingMap: UnderstandingMapState,
    val preferences: AppPreferences,
    /** Registered at launch — see [WakaRouteApplication.onCreate]. */
    val auth: AuthSession,
    /** Use this for every MANABU2 call. It is the only path that renews safely. */
    val authenticatedHttp: HttpClient,
    val profile: ProfileClient,
    val content: ContentClient,
    val actionQueue: LearningActionQueue,
    /**
     * Linking and signing in.
     *
     * Screens go through this rather than [auth] directly: signing in strands
     * anything still in [actionQueue], and this is what knows to deal with
     * that first.
     */
    val handover: AccountHandover,
    /**
     * 学習記録の削除 — required by App Store Review 5.1.1(v) and Google Play's
     * data-deletion policy, because first launch creates an account.
     */
    val accountDeletion: AccountDeletion,
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
                // 数学 is bundled; the other four 教科 appear when their edges
                // are published, without a release. See PublishedGraphCatalogue.
                understandingMap = run {
                    val graphStore = FilePrerequisiteGraphStore(
                        File(application.filesDir, "prerequisite-graphs.json"),
                    )
                    val catalogue = PublishedGraphCatalogue(graphStore)
                    val structure = GraphUnderstandingMapRepository(catalogue)

                    UnderstandingMapState(
                        bundled = structure,
                        live = LiveUnderstandingMap(content, structure, catalogue),
                        // Unauthenticated, like the school catalogue: the
                        // prerequisite structure is not learner data, and
                        // fetching it must not create an account.
                        graphs = PrerequisiteGraphSync(
                            PrerequisiteGraphClient(http, environment),
                            graphStore,
                        ),
                    )
                },
                content = content,
                actionQueue = queue,
                handover = AccountHandover(auth, queue),
                accountDeletion = AccountDeletion(auth, queue),
                studyTimer = StudyTimer(queue),
                preferences = AppPreferences(application),
                auth = auth,
                authenticatedHttp = authenticated,
                profile = ProfileClient(authenticated, environment),
                targetSchools = TargetSchoolsState(
                    repository = HttpTargetSchoolsRepository(authenticated, environment),
                ),
            )
        }
    }
}
