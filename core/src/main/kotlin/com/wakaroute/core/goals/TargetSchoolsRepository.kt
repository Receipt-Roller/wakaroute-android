package com.wakaroute.core.goals

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.WakaRouteJson
import com.wakaroute.core.net.retryingReads
import com.wakaroute.core.net.sendDecoding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The learner's 志望校.
 *
 * `PUT` **replaces the whole list** — there is no add or remove endpoint. Every
 * change is therefore read-modify-write, and that is the interesting part of
 * this class rather than an implementation detail: two edits racing would
 * silently drop one of them, and a 志望校 vanishing without the student touching
 * it is exactly the kind of thing they will not report and will not trust.
 */
interface TargetSchoolsRepository {
    suspend fun load(): TargetSchoolList

    /** Appends, or moves an existing entry's date. Order is preserved. */
    suspend fun add(schoolId: String, name: String, examDate: String? = null): TargetSchoolList

    suspend fun remove(schoolId: String): TargetSchoolList

    /** Reorders to exactly [schoolIds]; 第一志望 first. Unknown ids are ignored. */
    suspend fun reorder(schoolIds: List<String>): TargetSchoolList
}

class HttpTargetSchoolsRepository(
    private val http: HttpClient,
    private val environment: AppEnvironment,
) : TargetSchoolsRepository {

    /**
     * Serialises the read-modify-write cycle.
     *
     * Tapping 志望校に追加 on two schools quickly is enough to lose one without
     * this: both reads see the old list, and the second write overwrites the
     * first. Within one process this is sufficient; across devices the last
     * write still wins, which the API's replace-everything shape makes
     * unavoidable.
     */
    private val mutex = Mutex()

    override suspend fun load(): TargetSchoolList = retryingReads {
        http.sendDecoding(
            HttpRequest(
                method = HttpRequest.Method.GET,
                url = url(),
                headers = mapOf("Accept" to "application/json"),
            ),
            TargetSchoolList.serializer(),
        )
    }

    override suspend fun add(schoolId: String, name: String, examDate: String?) = mutex.withLock {
        val current = load().goals
        val without = current.filterNot { it.externalId == schoolId }

        // Appended, not promoted. The student decides which school is 第一志望;
        // adding one should not silently demote the one they already chose.
        write(
            without + TargetSchool(
                externalId = schoolId,
                name = name,
                targetDate = examDate,
            ),
        )
    }

    override suspend fun remove(schoolId: String) = mutex.withLock {
        write(load().goals.filterNot { it.externalId == schoolId })
    }

    override suspend fun reorder(schoolIds: List<String>) = mutex.withLock {
        val byId = load().goals.associateBy { it.externalId }
        write(schoolIds.mapNotNull(byId::get))
    }

    private suspend fun write(goals: List<TargetSchool>): TargetSchoolList {
        val body = TargetSchoolListWrite(
            type = TargetSchool.HIGH_SCHOOL,
            goals = goals.map {
                TargetSchoolWrite(
                    source = it.source,
                    externalId = it.externalId.take(TargetSchool.MAX_EXTERNAL_ID_LENGTH),
                    // Truncated rather than rejected. The API returns 400 above
                    // 200 characters, and losing the tail of a long school name
                    // is better than losing the student's whole 志望校 list.
                    name = it.name.take(TargetSchool.MAX_NAME_LENGTH),
                    targetDate = it.targetDate,
                )
            },
        )

        // Not retried. This is a write, and the whole list is the payload —
        // a repeat is harmless in effect but would race with a concurrent edit
        // in a way a read never can.
        return http.sendDecoding(
            HttpRequest(
                method = HttpRequest.Method.PUT,
                url = url(),
                headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
                body = WakaRouteJson.encodeToString(TargetSchoolListWrite.serializer(), body),
            ),
            TargetSchoolList.serializer(),
        )
    }

    private fun url() = environment.manabu2BaseUrl.trimEnd('/') + "/api/v1/me/target-schools"
}
