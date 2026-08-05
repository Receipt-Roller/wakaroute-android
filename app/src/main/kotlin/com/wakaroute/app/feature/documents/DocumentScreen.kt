package com.wakaroute.app.feature.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.design.DocumentBlockView
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.app.ui.theme.WakaRouteTheme
import com.wakaroute.core.documents.BundledDocument
import com.wakaroute.core.documents.DocumentBlock
import com.wakaroute.core.documents.InlineSpan

/**
 * Renders a bundled document.
 *
 * No WebView, by §3. The text is laid out with the app's own typography, so it
 * follows the student's text size and TalkBack reads it as ordinary content
 * rather than as a web page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(document: BundledDocument, onBack: () -> Unit) {
    val blocks = remember(document) { document.blocks() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (document.describesUnbuiltFeatures) {
                item { PhaseOneNotice() }
            }

            items(blocks) { block ->
                ReadableColumn { DocumentBlockView(block) }
            }
        }
    }
}

/**
 * The gap between what the documents say and what this build does.
 *
 * The four legal documents are verbatim copies of the Web text, because §3
 * requires 同一内容 — so they describe 学習記録, クイズ and 志望校 that Phase 1 does
 * not have. Editing them to match would put the app's terms out of step with
 * the published ones, which is not a change to make in a client repository.
 *
 * Shown beside the text instead, and raised as an AB question. See AGENTS.md.
 */
@Composable
private fun PhaseOneNotice() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = BundledDocument.PHASE_1_CAVEAT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp),
        )
    }
}

/**
 * True when the document talks about features Phase 1 does not have.
 *
 * 高校受験とは was written for this build and needs no caveat; the four copied
 * from the Web describe the finished product.
 */
private val BundledDocument.describesUnbuiltFeatures: Boolean
    get() = this != BundledDocument.ExamGuide

@Preview(showBackground = true)
@Composable
private fun DocumentPreview() {
    WakaRouteTheme { DocumentScreen(BundledDocument.ExamGuide, onBack = {}) }
}
