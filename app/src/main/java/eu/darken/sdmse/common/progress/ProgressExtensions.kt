package eu.darken.sdmse.common.progress

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.StringRes
import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.toCaDrawable
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlin.coroutines.EmptyCoroutineContext

fun <T : Progress.Client> T.updateProgressIcon(icon: Drawable) {
    updateProgress { (it ?: Progress.Data()).copy(icon = icon.toCaDrawable()) }
}

fun <T : Progress.Client> T.updateProgressIcon(icon: CaDrawable?) {
    updateProgress { (it ?: Progress.Data()).copy(icon = icon) }
}

fun <T : Progress.Client> T.updateProgressIcon(resolv: (Context) -> Drawable) {
    updateProgress { (it ?: Progress.Data()).copy(icon = resolv.toCaDrawable()) }
}

fun <T : Progress.Client> T.updateProgressPrimary(primary: String) {
    updateProgress { (it ?: Progress.Data()).copy(primary = primary.toCaString()) }
}

fun <T : Progress.Client> T.updateProgressPrimary(primary: CaString) {
    updateProgress { (it ?: Progress.Data()).copy(primary = primary) }
}

fun <T : Progress.Client> T.updateProgressPrimary(resolv: (Context) -> String) {
    updateProgress { (it ?: Progress.Data()).copy(primary = caString { resolv(this) }) }
}

fun <T : Progress.Client> T.updateProgressPrimary(@StringRes primary: Int, vararg args: Any) {
    updateProgress { (it ?: Progress.Data()).copy(primary = (primary to args).toCaString()) }
}

fun <T : Progress.Client> T.updateProgressSecondary(secondary: String) {
    updateProgress { (it ?: Progress.Data()).copy(secondary = secondary.toCaString()) }
}

fun <T : Progress.Client> T.updateProgressSecondary(resolv: (Context) -> String) {
    updateProgress { (it ?: Progress.Data()).copy(secondary = caString { resolv(this) }) }
}

fun <T : Progress.Client> T.updateProgressSecondary(secondary: CaString = CaString.EMPTY) {
    updateProgress { (it ?: Progress.Data()).copy(secondary = secondary) }
}

fun <T : Progress.Client> T.updateProgressSecondary(@StringRes secondary: Int, vararg args: Any) {
    updateProgress { (it ?: Progress.Data()).copy(secondary = (secondary to args).toCaString()) }
}

fun <T : Progress.Client> T.updateProgressCount(count: Progress.Count) {
    updateProgress { (it ?: Progress.Data()).copy(count = count) }
}

fun <T : Progress.Client> T.increaseProgress(value: Int = 1) {
    updateProgress {
        when (it?.count) {
            is Progress.Count.Counter -> it.copy(count = (it.count as Progress.Count.Counter).increment(value))
            is Progress.Count.Percent -> it.copy(count = (it.count as Progress.Count.Percent).increment(value))
            else -> {
                log(ERROR) { "Can't increaseProgress() on type: ${it?.count}" }
                it
            }
        }
    }
}

suspend fun <T : Progress.Host> T.forwardProgressTo(
    client: Progress.Client,
    onUpdate: (existing: Progress.Data?, new: Progress.Data?) -> Progress.Data?,
    onCompletion: (Progress.Data?) -> Progress.Data?,
) = progress
    .onEach { new -> client.updateProgress { onUpdate(it, new) } }
    .onCompletion { if (currentCoroutineContext().isActive) client.updateProgress { onCompletion(it) } }

suspend fun <T : Progress.Host, R> T.withProgress(
    client: Progress.Client,
    onUpdate: (existing: Progress.Data?, new: Progress.Data?) -> Progress.Data? = { _, new -> new },
    onCompletion: (Progress.Data?) -> Progress.Data? = { Progress.Data() },
    action: suspend T.() -> R
): R {
    val scope = CoroutineScope(EmptyCoroutineContext)

    val forwardingJob = forwardProgressTo(
        client,
        onUpdate,
        onCompletion
    ).launchIn(scope)

    return try {
        action()
    } finally {
        forwardingJob.cancelAndJoin()
        scope.cancel("Finished scope")
    }
}