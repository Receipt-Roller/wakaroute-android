package com.wakaroute.app.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wakaroute.core.net.ApiError

/** A loading spinner or a short message, centred in what the Scaffold leaves. */
@Composable
fun Centered(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().padding(padding).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 「電波がない」 and 「サーバーが落ちている」 are different things to a student. */
fun ApiError.studentFacingMessage(): String = when (this) {
    is ApiError.Offline -> "インターネットにつながっていないようです。"
    is ApiError.TimedOut -> "時間内に返事がありませんでした。"
    else -> "いま読み込めませんでした。しばらくしてから、もう一度ためしてください。"
}
