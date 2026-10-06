package com.wakaroute.core.journal

/**
 * One block of time — one the server has, or one still waiting to be sent.
 *
 * Drawn the same way on purpose: the student recorded both, and which has
 * finished uploading is not something they should have to think about. A
 * queued block has no [entryId] yet, so it cannot be deleted.
 */
data class DayLogRow(
    val id: String,
    val category: String,
    val durationMinutes: Int,
    val subject: String?,
    val content: String?,
    /** Null until the server has it. */
    val entryId: String?,
) {
    val isSent: Boolean get() = entryId != null

    /** 数学・p.42 — whichever the student filled in. */
    val detailLine: String?
        get() = listOfNotNull(subject, content).filter { it.isNotBlank() }.joinToString("・").ifEmpty { null }
}

/**
 * A day as the student should see it: the server's record, plus what this
 * device wrote and has not managed to send.
 *
 * The outbox alone is not enough. A diary kept safe on disk while the screen
 * shows it vanishing is, from where the student sits, a lost diary. The same
 * composition as iOS's `JournalDayRecord`.
 */
data class JournalDayRecord(
    val diary: DiaryEntry?,
    /** The diary on screen is one the server has not acknowledged yet. */
    val isDiaryUnsent: Boolean,
    val rows: List<DayLogRow>,
    val autoStudy: List<AutoStudyEntry>,
    val practice: PracticeSummary?,
    val studySelfMinutes: Int,
    val studyAutoMinutes: Int,
    /** Largest first. */
    val minutesByCategory: List<Pair<String, Int>>,
    val unsentCount: Int,
    /** Anything at all to show — distinguishes 「読み込めませんでした」 from an empty day. */
    val hasContent: Boolean,
) {
    val headline: StudySource get() = headlineStudySource(studySelfMinutes, studyAutoMinutes)

    val remainingMinutes: Int
        get() = (JournalLimits.MINUTES_PER_DAY - minutesByCategory.sumOf { it.second }).coerceAtLeast(0)

    companion object {
        fun of(date: String, day: JournalDay?, unsent: List<PendingJournalWrite>): JournalDayRecord {
            val forThisDay = unsent.filter { it.date == date && !it.rejected }

            // The last diary write wins: the endpoint is a full replace.
            val (diary, isDiaryUnsent) = when (val queued = forThisDay.lastOrNull { it.isDiaryWrite }?.payload) {
                is PendingJournalWrite.Payload.Diary -> queued.draft.asEntry(date) to true
                PendingJournalWrite.Payload.DeleteDiary -> null to true
                else -> day?.diary to false
            }

            val sent = day?.entries.orEmpty().map {
                DayLogRow(it.entryId, it.category, it.durationMinutes, it.subject, it.content, entryId = it.entryId)
            }

            // A block the server accepted can still be in the outbox if its reply
            // was lost. Matching on clientEntryId — what the server deduplicates
            // on — keeps it from being drawn, and counted, twice.
            val landed = day?.entries.orEmpty().mapNotNull { it.clientEntryId }.toSet()
            val queued = forThisDay
                .mapNotNull { (it.payload as? PendingJournalWrite.Payload.Entry)?.entry }
                .filter { it.clientEntryId !in landed }
                .map { DayLogRow(it.clientEntryId, it.category, it.durationMinutes, it.subject, it.content, entryId = null) }

            // Queued minutes count: they are the student's own entries. Leaving
            // them out would put 「自主学習 60分」 above a total of 0分.
            val minutes = day?.totals?.minutesByCategory.orEmpty().toMutableMap()
            for (row in queued) minutes[row.category] = (minutes[row.category] ?: 0) + row.durationMinutes

            return JournalDayRecord(
                diary = diary,
                isDiaryUnsent = isDiaryUnsent,
                rows = sent + queued,
                autoStudy = day?.autoStudy.orEmpty(),
                practice = day?.practice,
                studySelfMinutes = (day?.totals?.studySelfMinutes ?: 0) +
                    queued.filter { JournalCategories.countsAsStudy(it.category) }.sumOf { it.durationMinutes },
                studyAutoMinutes = day?.totals?.studyAutoMinutes ?: 0,
                minutesByCategory = minutes.toList().sortedByDescending { it.second },
                unsentCount = forThisDay.size,
                hasContent = day != null || forThisDay.isNotEmpty(),
            )
        }

        private fun DiaryDraft.asEntry(date: String) = DiaryEntry(
            date = date,
            achievements = achievements.ifBlank { null },
            struggles = struggles.ifBlank { null },
            tomorrowPlan = tomorrowPlan.ifBlank { null },
            focus = focus,
            fatigue = fatigue,
        )
    }
}
