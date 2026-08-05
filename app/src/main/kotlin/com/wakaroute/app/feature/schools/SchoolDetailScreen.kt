package com.wakaroute.app.feature.schools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.schools.SchoolAdmissionResult
import com.wakaroute.core.schools.SchoolDetail
import com.wakaroute.core.schools.SchoolExamSchedule
import com.wakaroute.core.schools.SchoolsRepository
import java.net.URLDecoder

private sealed interface DetailState {
    data object Loading : DetailState
    data class Loaded(val detail: SchoolDetail) : DetailState
    data class Failed(val failure: SearchFailure) : DetailState

    /** The id is no longer in the catalogue. Distinct from a network failure. */
    data object NotFound : DetailState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolDetailScreen(
    schools: SchoolsRepository,
    schoolId: String,
    onBack: () -> Unit,
) {
    val id = remember(schoolId) { URLDecoder.decode(schoolId, "UTF-8") }
    var state by remember(id) { mutableStateOf<DetailState>(DetailState.Loading) }

    LaunchedEffect(id) {
        state = try {
            DetailState.Loaded(schools.detail(id))
        } catch (e: ApiError.Http) {
            if (e.status == 404) DetailState.NotFound else DetailState.Failed(SearchFailure.Server)
        } catch (e: ApiError) {
            DetailState.Failed(
                when (e) {
                    is ApiError.Offline -> SearchFailure.Offline
                    is ApiError.TimedOut -> SearchFailure.TimedOut
                    is ApiError.Decoding -> SearchFailure.Unreadable
                    else -> SearchFailure.Server
                },
            )
        }
    }

    val loaded = state as? DetailState.Loaded

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(loaded?.detail?.school?.name ?: "高校") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                DetailState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                DetailState.NotFound -> Notice(
                    title = "この高校は見つかりませんでした",
                    body = "掲載されなくなった可能性があります。もう一度検索してみてください。",
                )

                is DetailState.Failed -> Notice(
                    title = "読み込めませんでした",
                    body = current.failure.message,
                    actionLabel = if (current.failure.canRetry) "もう一度ためす" else null,
                    onAction = { state = DetailState.Loading },
                )

                is DetailState.Loaded -> DetailBody(current.detail)
            }
        }
    }
}

@Composable
private fun DetailBody(detail: SchoolDetail) {
    val school = detail.school

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ReadableColumn(spacing = 16.dp) {
            school.nameKana?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Field("設置区分", school.ownershipDisplay)
            Field("所在地", listOfNotNull(school.postalCode?.let { "〒$it" }, school.address).joinToString(" ").ifBlank { null })
            Field("校地", school.campusTypeLabel)
            Field("公式サイト", school.officialUrl)

            detail.latestDeviationScore?.let { score ->
                HorizontalDivider()
                Text("偏差値", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = listOfNotNull(
                        score.displayText,
                        score.population,
                        score.provider?.let { "出典: $it" },
                        "${score.academicYear}年度",
                    ).joinToString("　"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (detail.examSchedules.isNotEmpty()) {
                HorizontalDivider()
                Text("入試日程", style = MaterialTheme.typography.titleMedium)
                for (schedule in detail.examSchedules) {
                    ExamSchedule(schedule)
                }
            }

            if (detail.admissions.isNotEmpty()) {
                HorizontalDivider()
                Text("入試結果", style = MaterialTheme.typography.titleMedium)
                for (result in detail.admissions) {
                    AdmissionResult(result)
                }
            }

            // The catalogue publishes structure before data: on 2026-08-02 every
            // school sampled had all three collections empty. Absence is normal
            // and is said so, rather than looking like a loading failure.
            if (detail.examSchedules.isEmpty() && detail.admissions.isEmpty() && detail.deviationScores.isEmpty()) {
                HorizontalDivider()
                Text(
                    text = "入試日程・入試結果・偏差値は、まだ掲載されていません。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            Text(
                text = "出願や受験の手続きは、必ず各高校や教育委員会の発表を確認してください。" +
                    "ここに載っている情報は最新でない場合があります。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun ExamSchedule(schedule: SchoolExamSchedule) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = listOfNotNull("${schedule.academicYear}年度", schedule.selectionLabel).joinToString("　"),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = when {
                schedule.testDates.isNotEmpty() -> "試験日: ${schedule.testDates.joinToString("、")}"
                // 未発表 is a real state — the schedule exists before its dates
                // are fixed — and is not the same as missing data.
                else -> schedule.statusLabel ?: "試験日は未発表です"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AdmissionResult(result: SchoolAdmissionResult) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = listOfNotNull("${result.academicYear}年度", result.selectionLabel, result.department)
                .joinToString("　"),
            style = MaterialTheme.typography.bodyLarge,
        )

        val ratio = result.competitionRatio
        if (ratio != null) {
            // Always labelled with which 倍率 it is. 実質倍率 1.22 shown as plain
            // 「倍率」 beside a school's published 志願倍率 1.30 reads as an error.
            Text(
                text = "${ratio.kind.label} ${"%.2f".format(ratio.value)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = ratio.kind.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = listOfNotNull(
                result.capacity?.let { "定員 $it" },
                result.applicants?.let { "志願 $it" },
                result.examinees?.let { "受験 $it" },
                result.admitted?.let { "合格 $it" },
            ).joinToString("　"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Field(label: String, value: String?) {
    if (value.isNullOrBlank()) return

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Notice(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        ReadableColumn(spacing = 12.dp) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
