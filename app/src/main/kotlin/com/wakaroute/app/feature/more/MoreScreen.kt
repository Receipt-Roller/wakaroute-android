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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.core.config.AppEnvironment
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

@Composable
private fun DocumentRow(title: String, onClick: () -> Unit) {
    AdaptiveRow(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
