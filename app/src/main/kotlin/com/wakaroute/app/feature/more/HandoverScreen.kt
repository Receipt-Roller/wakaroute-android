package com.wakaroute.app.feature.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.core.auth.AccountHandover
import com.wakaroute.core.net.ApiError
import kotlinx.coroutines.launch

/**
 * Which half of the handover this screen is.
 *
 * Two screens rather than one with a toggle. They read almost identically and
 * do opposite things — one saves what is here, the other replaces it — and a
 * student who picks the wrong tab of a combined screen loses the very thing
 * they opened it to protect.
 */
enum class HandoverMode {
    /** Attach an email to this device's record, so a new phone can reach it. */
    Link,

    /** Take over a record made on a previous phone. */
    SignIn,
}

private sealed interface HandoverPhase {
    data object Editing : HandoverPhase
    data object Working : HandoverPhase
    data class Done(val email: String) : HandoverPhase
    data class Failed(val message: String) : HandoverPhase

    /** Signing in now would strand study time recorded on this device. */
    data class UnsentWork(val records: Int) : HandoverPhase
}

/**
 * 学習記録の引き継ぎ.
 *
 * Framed throughout as protecting a record, never as 「アカウントを作る」 — §6.
 * A 中学生 does not want an account; they want the three years of work not to
 * disappear with the phone. The words follow from that, and so does the fact
 * that this screen exists at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandoverScreen(
    handover: AccountHandover,
    mode: HandoverMode,
    onBack: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf<HandoverPhase>(HandoverPhase.Editing) }
    val scope = rememberCoroutineScope()

    fun submit(discardingUnsentWork: Boolean = false) {
        phase = HandoverPhase.Working
        scope.launch {
            phase = try {
                when (mode) {
                    HandoverMode.Link -> {
                        handover.link(email.trim(), password)
                        HandoverPhase.Done(email.trim())
                    }

                    HandoverMode.SignIn -> when (
                        val result = handover.signIn(email.trim(), password, discardingUnsentWork)
                    ) {
                        is AccountHandover.SignInResult.SignedIn -> HandoverPhase.Done(email.trim())
                        is AccountHandover.SignInResult.Refused ->
                            HandoverPhase.UnsentWork(result.unsent.pendingRecords)
                    }
                }
            } catch (e: ApiError) {
                HandoverPhase.Failed(e.handoverMessage(mode))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (mode) {
                            HandoverMode.Link -> "記録を引き継げるようにする"
                            HandoverMode.SignIn -> "記録を呼び出す"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            ReadableColumn(spacing = 16.dp) {
                when (val current = phase) {
                    is HandoverPhase.Done -> DoneMessage(mode, current.email, onBack)

                    is HandoverPhase.UnsentWork -> UnsentWorkWarning(
                        records = current.records,
                        onRetry = { submit() },
                        onDiscard = { submit(discardingUnsentWork = true) },
                    )

                    else -> {
                        Text(
                            text = when (mode) {
                                HandoverMode.Link ->
                                    "メールアドレスを登録しておくと、スマホを変えても、" +
                                        "いまの学習記録をそのまま続けられます。"

                                HandoverMode.SignIn ->
                                    "前に使っていたメールアドレスとパスワードを入れると、" +
                                        "そのときの学習記録を呼び出せます。"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        if (mode == HandoverMode.SignIn) {
                            // Said before they type, not after they have
                            // committed. This device's record is not merged
                            // into the one being called up — it is left behind.
                            Text(
                                text = "このスマホでいま使っている記録は、呼び出した記録に置きかわります。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("メールアドレス") },
                            singleLine = true,
                            enabled = current !is HandoverPhase.Working,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("パスワード") },
                            singleLine = true,
                            enabled = current !is HandoverPhase.Working,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            // The rule is stated before the attempt. Learning it
                            // from a rejection means typing a password twice and
                            // being told off in between.
                            supportingText = if (mode == HandoverMode.Link) {
                                { Text(PASSWORD_REQUIREMENT) }
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (current is HandoverPhase.Failed) {
                            Text(
                                text = current.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        if (current is HandoverPhase.Working) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            Button(
                                onClick = { submit() },
                                enabled = canSubmit(email, password),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    when (mode) {
                                        HandoverMode.Link -> "登録する"
                                        HandoverMode.SignIn -> "記録を呼び出す"
                                    },
                                )
                            }
                        }

                        Text(
                            text = "メールアドレスは、記録を取り出すためだけに使います。" +
                                "お知らせや広告を送ることはありません。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Whether the server is worth troubling.
 *
 * Deliberately loose — an `@` and six characters. Stricter client-side email
 * rules reject addresses that work, and the only authority on whether these
 * credentials are usable is the server.
 */
private fun canSubmit(email: String, password: String) =
    email.trim().contains("@") && password.length >= MINIMUM_PASSWORD_LENGTH

/** The server's rule, stated in the words it will judge by. */
private const val PASSWORD_REQUIREMENT =
    "6文字以上で、大文字・小文字・記号をそれぞれ1つ以上入れてください。"

private const val MINIMUM_PASSWORD_LENGTH = 6

@Composable
private fun DoneMessage(mode: HandoverMode, email: String, onBack: () -> Unit) {
    Text(text = "できました", style = MaterialTheme.typography.headlineSmall)

    Text(
        text = when (mode) {
            HandoverMode.Link -> "$email で、新しいスマホからも学習記録を続けられます。"
            HandoverMode.SignIn -> "$email の学習記録を呼び出しました。"
        },
        style = MaterialTheme.typography.bodyMedium,
    )

    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("閉じる") }
}

/**
 * The one moment where a student can lose work by continuing.
 *
 * Never resolved quietly. They are told how much is at stake, offered the fix
 * that costs nothing — try again with a signal — and only then given the way
 * through that throws it away.
 */
@Composable
private fun UnsentWorkWarning(records: Int, onRetry: () -> Unit, onDiscard: () -> Unit) {
    Text(text = "まだ送れていない記録があります", style = MaterialTheme.typography.headlineSmall)

    Text(
        text = "このスマホに、まだ送れていない学習の記録が $records 件あります。" +
            "いま記録を呼び出すと、この $records 件は消えてしまいます。",
        style = MaterialTheme.typography.bodyMedium,
    )

    Text(
        text = "電波の良いところでもう一度ためすと、送ってから続けられます。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("もう一度ためす") }

    OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) {
        Text("$records 件を消して続ける")
    }
}

/**
 * What the student is told when it fails.
 *
 * Branches on the server's `code`, never on `detail` — the backend guide says
 * the prose changes, and a client that reads it breaks on a copy edit. The one
 * exception is 400, where the server names the password rule that was missed
 * and is more use than anything generic written here.
 */
private fun ApiError.handoverMessage(mode: HandoverMode): String = when {
    this is ApiError.Offline -> "インターネットにつながっていないようです。"
    this is ApiError.TimedOut -> "時間内に返事がありませんでした。もう一度ためしてください。"

    this is ApiError.Http && code == "already_linked" ->
        "このスマホの記録には、すでにメールアドレスが登録されています。"

    this is ApiError.Http && status == 409 -> when (mode) {
        // Not a dead end: the record behind that address is reachable, just not
        // from here. Pointing at the other screen turns a refusal into a step.
        HandoverMode.Link ->
            "このメールアドレスは、別の記録で使われています。" +
                "「記録を呼び出す」からログインすると、その記録を続けられます。"

        HandoverMode.SignIn -> "このメールアドレスは使えません。"
    }

    this is ApiError.Http && status == 400 ->
        problem?.detail?.takeIf { it.isNotBlank() } ?: PASSWORD_REQUIREMENT

    this is ApiError.Http && status == 401 -> "メールアドレスかパスワードが違います。"

    else -> "うまくいきませんでした。もう一度ためしてください。"
}
