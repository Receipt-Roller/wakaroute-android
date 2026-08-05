package com.wakaroute.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.app.ui.theme.WakaRouteTheme

/**
 * 初回説明 — shown once, and skippable.
 *
 * iOS has no equivalent screen, so the wording is new. Three constraints shaped
 * it and are worth stating, because the obvious version of this screen breaks
 * all three:
 *
 * 1. **It is not a sign-up.** No account, no email, no password — that is the
 *    product decision, and the intro is where a student first sees it kept.
 * 2. **It describes only what this build does.** 学習履歴, クイズ and 志望校
 *    belong to Phase 2, so they are named as 準備中 rather than promised.
 * 3. **It does not predict results.** 「合格できます」 in any form is ruled out by
 *    the 利用規約 and the 保護者向けガイド alike.
 */
@Composable
fun IntroductionScreen(onFinish: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ReadableColumn(spacing = 20.dp) {
                Spacer(Modifier.padding(top = 24.dp))

                Text(
                    text = "ワカルートへようこそ",
                    style = MaterialTheme.typography.headlineMedium,
                )

                Text(
                    text = "高校受験にむけて学ぶ人のための、無料のアプリです。" +
                        "登録もお金もいりません。名前もメールアドレスも聞きません。",
                    style = MaterialTheme.typography.bodyLarge,
                )

                IntroPoint(
                    icon = Icons.Filled.SubdirectoryArrowLeft,
                    title = "つまずいた「その手前」に戻ります",
                    body = "一次関数が解けないとき、原因が一次関数にあるとはかぎりません。" +
                        "ワカルートは項目どうしのつながりから、戻るべき場所をさがします。",
                )

                IntroPoint(
                    icon = Icons.Outlined.AccountTree,
                    title = "理解マップで全体を見る",
                    body = "教科の中がどんな項目に分かれていて、何が何の前提になっているのかを見られます。" +
                        "いまは数学だけです。ほかの4教科は準備中です。",
                )

                IntroPoint(
                    icon = Icons.Filled.School,
                    title = "高校を探す",
                    body = "全国の高校を、キーワード・都道府県・設置区分でさがせます。" +
                        "文部科学省の学校コードをもとにしたデータです。",
                )

                Text(
                    text = "いまできないこと",
                    style = MaterialTheme.typography.titleMedium,
                )

                Text(
                    text = "学習の記録、レッスンとクイズ、志望校の登録は、まだ動いていません。" +
                        "できあがるまで、アプリの中では「準備中」と表示されます。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "ワカルートは、入試の合否を予想するものではありません。" +
                        "志望校を決めるときは、学校の先生や家の人と相談してください。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("はじめる", textAlign = TextAlign.Center)
                }

                Spacer(Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

@Composable
private fun IntroPoint(icon: ImageVector, title: String, body: String) {
    AdaptiveRow(
        modifier = Modifier.fillMaxWidth(),
        // Top, not centre. Beside a three-line paragraph a centred icon floats
        // in the middle of the text with nothing to relate to.
        verticalAlignment = Alignment.Top,
    ) { flexible ->
        Icon(
            imageVector = icon,
            // The heading beside it says the same thing; announcing both makes
            // TalkBack read every point twice.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Column(
            modifier = flexible,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun IntroductionPreview() {
    WakaRouteTheme { IntroductionScreen(onFinish = {}) }
}

/** The same screen at the text size that used to break the icon rows. */
@Preview(showBackground = true, fontScale = 2.0f, heightDp = 900)
@Composable
private fun IntroductionLargeTextPreview() {
    WakaRouteTheme { IntroductionScreen(onFinish = {}) }
}
