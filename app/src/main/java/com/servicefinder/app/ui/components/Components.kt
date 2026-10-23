package com.servicefinder.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.servicefinder.app.R
import com.servicefinder.app.data.Ref
import com.servicefinder.app.data.Source

/** Source documents by id, provided once at the root so any screen can cite them. */
val LocalSources = compositionLocalOf<Map<String, Source>> { emptyMap() }

@Composable
fun AppIcon(@DrawableRes id: Int, contentDescription: String?, modifier: Modifier = Modifier) {
    Icon(painterResource(id), contentDescription, modifier)
}

/** Human-readable page reference: "p. 8-15 (PDF 675)". */
fun Ref.pageText(): String? = when {
    page == null -> null
    label != null && label != page.toString() -> "p. $label (PDF $page)"
    else -> "PDF p. $page"
}

/**
 * The citation shown under every figure: source title and page. Tapping it
 * shows the source details, including where to get the document.
 */
@Composable
fun Citation(ref: Ref, modifier: Modifier = Modifier) {
    val source = LocalSources.current[ref.sourceId]
    var open by remember { mutableStateOf(false) }
    val text = listOfNotNull(source?.title ?: ref.sourceId, ref.pageText(), ref.detail).joinToString(" · ")
    Row(
        modifier
            .clickable(role = Role.Button, onClickLabel = "Show source details") { open = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppIcon(R.drawable.ic_book, contentDescription = null, modifier = Modifier.size(16.dp).padding(top = 1.dp))
        Text(
            "Source: $text",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open) SourceDialog(source, ref, onDismiss = { open = false })
}

@Composable
fun SourceDialog(source: Source?, ref: Ref?, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(source?.title ?: ref?.sourceId ?: "Source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                source?.publisher?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                source?.market?.let { Text("Market: $it", style = MaterialTheme.typography.bodyMedium) }
                ref?.pageText()?.let { Text("Page: $it", style = MaterialTheme.typography.bodyMedium) }
                ref?.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                source?.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = source?.url?.let { url ->
            { TextButton(onClick = { runCatching { uri.openUri(url) } }) { Text("Open document") } }
        },
    )
}

/** Shown in place of any value the sources do not give. Values are never estimated. */
@Composable
fun NotAvailable(reason: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            "Not available",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        reason?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() }.padding(top = 16.dp, bottom = 8.dp),
    )
}

/** A card with an icon and title, used for the quick-menu tiles and detail sections. */
@Composable
fun InfoCard(
    title: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val inner: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIcon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (onClick != null) AppIcon(R.drawable.ic_chevron_right, contentDescription = null, Modifier.size(20.dp))
            }
            content()
        }
    }
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    if (onClick != null) Card(onClick = onClick, modifier = modifier, colors = colors) { inner() }
    else Card(modifier = modifier, colors = colors) { inner() }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String = "Loading") {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun MessageState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(icon, contentDescription = null, modifier = Modifier.size(48.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.size(4.dp))
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    MessageState(R.drawable.ic_warning, "Something went wrong", message, modifier, "Try again", onRetry)
}

/** A numbered or bulleted list of plain sentences. */
@Composable
fun TextList(items: List<String>, modifier: Modifier = Modifier, numbered: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { i, s ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (numbered) "${i + 1}." else "•", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.widthIn(min = 18.dp))
                Text(s, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}
