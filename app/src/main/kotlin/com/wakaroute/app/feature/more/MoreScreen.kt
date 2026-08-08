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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.core.auth.AccountDeletion
import com.wakaroute.core.auth.AuthSession
import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.profile.ProfileClient
import com.wakaroute.core.documents.BundledDocument
import kotlinx.coroutines.launch

/**
 * その他 — the documents, the handover, and what this build is.
 */
@Composable
fun MoreScreen(
    environment: AppEnvironment,
    auth: AuthSession,
    profile: ProfileClient,
    deletion: AccountDeletion,
    onOpenDocument: (BundledDocument) -> Unit,
    onLink: () -> Unit,
    onSignIn: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
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
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReadableColumn(spacing = 8.dp) {
            Text(
                text = "ワカルートについて",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            LearningRecordSection(
                auth = auth,
                profile = profile,
                accountDeletion = deletion,
                onLink = onLink,
                onSignIn = onSignIn,
                // The delete button sits near the bottom, and confirming it
                // shortens the section above — so the 「削除しました」 that
                // replaces it lands off-screen, above where the student is
                // looking. They tapped something irreversible; they should not
                // have to go hunting for the answer.
                onDeleted = { scope.launch { scrollState.animateScrollTo(0) } },
            )

            // Every document is bundled, so this list works with no signal.
            // §3: a 中学生 tapping 利用規約 must not be handed to a browser.
            for (document in BundledDocument.entries) {
                DisclosureRow(document.title) { onOpenDocument(document) }
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
 * Where the student's records actually live, and how to move them.
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
private fun LearningRecordSection(
    auth: AuthSession,
    profile: ProfileClient,
    accountDeletion: AccountDeletion,
    onLink: () -> Unit,
    onSignIn: () -> Unit,
    onDeleted: () -> Unit,
) {
    var state by remember { mutableStateOf<RecordState>(RecordState.Loading) }
    var deletion by remember { mutableStateOf<DeletePhase>(DeletePhase.Idle) }

    // Re-read on every entry rather than once per process. Coming back from
    // 引き継ぎ having just linked, a cached 「引き継げません」 would be the first
    // thing the student sees.
    LaunchedEffect(Unit) {
        state = if (!auth.hasAccount()) {
            RecordState.NoAccount
        } else {
            try {
                RecordState.Known(profile.profile().isLinked)
            } catch (e: ApiError) {
                RecordState.Unknown
            }
        }
    }

    if (state !is RecordState.Loading) {
        // Named to match the bundled 「ワカルートとは」, which tells students to
        // look under 「その他」→「学習記録とアカウント」. That document is kept
        // byte-identical with the Web and is not ours to edit, so the screen is
        // what moves.
        Text(
            text = "学習記録とアカウント",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )

        // Once deleted, this replaces the section rather than sitting under it.
        // Re-reading the account state instead would drop every row including
        // the confirmation, and the student would be left watching the thing
        // they tapped disappear with nothing said.
        if (deletion is DeletePhase.Done) {
            Text(
                text = "削除しました。このアプリは、また新しく始められます。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        } else {
            Text(
                text = when (val current = state) {
                    RecordState.NoAccount ->
                        "まだ何も保存していません。志望校を登録すると、この端末に記録がつくられます。"

                    is RecordState.Known -> if (current.isLinked) {
                        "学習記録はメールアドレスに紐づいています。スマホを変えても引き継げます。"
                    } else {
                        "学習記録はこの端末の中にあります。" +
                            "メールアドレスを登録しておくと、スマホを変えても続けられます。"
                    }

                    RecordState.Unknown -> "学習記録の状態をいま確認できませんでした。"

                    RecordState.Loading -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Nothing to offer a linked account. Linking again is a 409, and
            // 呼び出す would let one tap replace a protected record with another —
            // a footgun in front of the students who have the most to lose. iOS
            // hides both for the same reason.
            if ((state as? RecordState.Known)?.isLinked == false) {
                DisclosureRow("学習記録を引き継げるようにする", onLink)
                HorizontalDivider()
            }

            // Also offered with no account at all: that is precisely what a new
            // phone looks like, and it is the phone this feature exists for.
            if (state is RecordState.NoAccount || (state as? RecordState.Known)?.isLinked == false) {
                DisclosureRow("前の記録を呼び出す", onSignIn)
                HorizontalDivider()
            }

            // Offered whenever an account exists, linked or not. Required by App
            // Store Review 5.1.1(v) and Google Play's data-deletion policy: an app
            // that creates accounts must let the student delete one from inside
            // the app. ワカルート creates one silently on first launch, so this
            // applies even though there is no sign-up screen anywhere.
            if (state !is RecordState.NoAccount) {
                DeleteAccountSection(
                    deletion = accountDeletion,
                    phase = deletion,
                    onPhaseChange = {
                        deletion = it
                        if (it is DeletePhase.Done) onDeleted()
                    },
                )
            }
        }
    }
}

/**
 * 学習記録の削除.
 *
 * Deliberately not a disclosure row like the others. It is the only control on
 * this screen that destroys something, and it sits two taps from a student who
 * may have three years of work behind it.
 *
 * The confirmation **names what is lost**. 「本当によろしいですか」 tells a 中学生
 * nothing about what they are agreeing to.
 */
@Composable
private fun DeleteAccountSection(
    deletion: AccountDeletion,
    phase: DeletePhase,
    onPhaseChange: (DeletePhase) -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun delete() {
        confirming = false
        onPhaseChange(DeletePhase.Working)
        scope.launch {
            onPhaseChange(
                when (val result = deletion.delete()) {
                    is AccountDeletion.Result.Deleted -> DeletePhase.Done
                    is AccountDeletion.Result.Failed ->
                        DeletePhase.Failed(result.cause.deletionMessage())
                },
            )
        }
    }

    when (phase) {
        DeletePhase.Idle -> TextButton(onClick = { confirming = true }) {
            Text("学習記録を削除する", color = MaterialTheme.colorScheme.error)
        }

        DeletePhase.Working -> Text(
            text = "削除しています…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 14.dp),
        )

        // Handled by the caller, which replaces this whole section.
        DeletePhase.Done -> Unit

        is DeletePhase.Failed -> Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Says plainly that nothing was lost. A failed delete is the safe
            // direction to fail in, and a student who has just been told
            // 「消えます。もとに戻せません」 has every reason to assume the worst.
            Text(
                text = phase.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = { confirming = true }) { Text("もう一度ためす") }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("学習記録を削除しますか？") },
            text = {
                Text(
                    "学習の記録（レッスン・クイズ）、志望校、勉強した時間がすべて消えます。" +
                        "もとに戻すことはできません。",
                )
            },
            confirmButton = {
                TextButton(onClick = { delete() }) {
                    Text("削除する", color = MaterialTheme.colorScheme.error)
                }
            },
            // 「やめる」 is the plain-language opposite of 「削除する」. 「キャンセル」
            // is a loanword a 中学生 has to stop and parse.
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("やめる") } },
        )
    }
}

private sealed interface DeletePhase {
    data object Idle : DeletePhase
    data object Working : DeletePhase
    data object Done : DeletePhase
    data class Failed(val message: String) : DeletePhase
}

/**
 * Branches on `code`, never on `detail` — the backend guide says the prose
 * changes, so a client that reads it breaks on a copy edit.
 */
private fun ApiError.deletionMessage(): String = when {
    this is ApiError.Offline -> "インターネットにつながっていないようです。記録はそのまま残っています。"
    this is ApiError.TimedOut -> "時間内に返事がありませんでした。記録はそのまま残っています。"
    else -> "いま削除できませんでした。記録はそのまま残っています。"
}

private sealed interface RecordState {
    data object Loading : RecordState
    data object NoAccount : RecordState
    data class Known(val isLinked: Boolean) : RecordState
    data object Unknown : RecordState
}

@Composable
private fun DisclosureRow(title: String, onClick: () -> Unit) {
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
