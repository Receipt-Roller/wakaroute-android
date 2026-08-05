package com.wakaroute.app.feature.more

import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.core.auth.AuthSession
import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.profile.ProfileClient
import com.wakaroute.core.documents.BundledDocument

/**
 * その他 — the documents, and what this build is.
 *
 * 学習記録とアカウント lives here on iOS. It is absent rather than stubbed: there
 * is no account in Phase 1, and a row that opens onto 準備中 invites a student to
 * look for a setting that does not exist.
 */
@Composable
fun MoreScreen(
    environment: AppEnvironment,
    auth: AuthSession,
    profile: ProfileClient,
    onOpenDocument: (BundledDocument) -> Unit,
) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReadableColumn(spacing = 8.dp) {
            Text(
                text = "ワカルートについて",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            LearningRecordSection(auth, profile)

            // Every document is bundled, so this list works with no signal.
            // §3: a 中学生 tapping 利用規約 must not be handed to a browser.
            for (document in BundledDocument.entries) {
                DocumentRow(document.title) { onOpenDocument(document) }
                HorizontalDivider()
            }

            Text(
                text = "データについて",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp),
            )

            Text(
                text = "高校の一覧は、文部科学省「学校コード」（2025年5月1日時点）をもとにしています。" +
                    "政府標準利用規約2.0（CC BY 4.0 互換）にもとづいて利用しています。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "このアプリには、アクセス解析もクラッシュ収集も入っていません。" +
                    "どの画面を見たかを運営者が知ることはできません。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Text(
                text = buildString {
                    append("株式会社レシートローラー")
                    append("\nsupport@wakaroute.com")
                    versionName?.let { append("\nバージョン $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp, bottom = 24.dp),
            )
        }
    }
}

/**
 * Where the student's records actually live.
 *
 * The 利用規約 tells them this matters — an unlinked account dies with the
 * install — so the app owes them a straight answer rather than making them
 * infer it. Three states, and they are genuinely different things:
 *
 * - **no account yet**: nothing has been saved anywhere. Not a warning.
 * - **device only**: saved, but a new phone loses it.
 * - **linked**: survives a new phone.
 *
 * Only fetched when an account already exists, for the same reason as the home
 * screen: asking would create one.
 */
@Composable
private fun LearningRecordSection(auth: AuthSession, profile: ProfileClient) {
    var state by remember { mutableStateOf<RecordState>(RecordState.Loading) }

    LaunchedEffect(Unit) {
        state = if (!auth.isRegistered()) {
            RecordState.NoAccount
        } else {
            try {
                RecordState.Known(profile.profile().isLinked)
            } catch (e: ApiError) {
                RecordState.Unknown
            }
        }
    }

    val message = when (val current = state) {
        RecordState.Loading -> return
        RecordState.NoAccount ->
            "まだ何も保存していません。志望校を登録すると、この端末に記録がつくられます。"

        // Stated plainly, without alarm. Linking is Phase 2 の引き継ぎ and does
        // not exist yet, so telling a student to act on this now would point
        // them at a button that is not there.
        is RecordState.Known -> if (current.isLinked) {
            "学習記録はメールアドレスに紐づいています。スマホを変えても引き継げます。"
        } else {
            "学習記録はこの端末の中にあります。スマホを変えると引き継げません。引き継ぐしくみは準備中です。"
        }

        RecordState.Unknown ->
            "学習記録の状態をいま確認できませんでした。"
    }

    Text(
        text = "学習記録",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 16.dp),
    )
}

private sealed interface RecordState {
    data object Loading : RecordState
    data object NoAccount : RecordState
    data class Known(val isLinked: Boolean) : RecordState
    data object Unknown : RecordState
}

@Composable
private fun DocumentRow(title: String, onClick: () -> Unit) {
    AdaptiveRow(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = title }
            // 48dp keeps the target reachable at any text size; the row grows
            // beyond it when the label wraps.
            .padding(vertical = 14.dp),
    ) { flexible ->
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = flexible,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
