package com.wakaroute.app

import android.app.Application
import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.map.BundledUnderstandingMapRepository
import com.wakaroute.core.map.UnderstandingMapRepository
import com.wakaroute.core.net.OkHttpHttpClient
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
    val understandingMap: UnderstandingMapRepository,
    val preferences: AppPreferences,
) {
    companion object {
        fun live(application: Application): AppServices {
            val environment = AppEnvironment.Production
            val http = OkHttpHttpClient()

            return AppServices(
                environment = environment,
                schools = HttpSchoolsRepository(http, environment),
                // Phase 1 has no learner data of any kind, so the map is the
                // authored graph and an empty record. See
                // BundledUnderstandingMapRepository for why that is stated
                // rather than filled in.
                understandingMap = BundledUnderstandingMapRepository(),
                preferences = AppPreferences(application),
            )
        }
    }
}
