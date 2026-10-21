package com.sagoma.planimetria.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomPhoto
import kotlinx.coroutines.launch

/** Lato lungo della foto salvata (px): si leggono bene prese e misure sul metro, e pesa circa 300 KB. */
private const val PHOTO_SIDE = 1600
private const val PHOTO_QUALITY = 80

/** Miniatura per l'elenco: poche decine di KB, così una stanza con tante foto non riempie la memoria. */
private const val THUMB_SIDE = 360
private const val THUMB_QUALITY = 75

/** Miniature già lette (piccole: se ne tengono parecchie). */
private object PhotoThumbs {
    private val cache = LinkedHashMap<String, ImageBitmap>()
    fun get(key: String): ImageBitmap? = cache.remove(key)?.also { cache[key] = it }
    fun put(key: String, img: ImageBitmap) {
        cache[key] = img
        while (cache.size > 60) cache.remove(cache.keys.first())
    }
}

/** Immagine tra i file del progetto aperto; null mentre si legge o se manca. Con `cached` resta in memoria (miniature). */
@Composable
private fun rememberProjectImage(name: String, cached: Boolean): ImageBitmap? {
    val platform = LocalPlatform.current
    val projectId = LocalProjectId.current
    val key = projectId?.let { "$it/$name" }
    val img by produceState(key?.takeIf { cached }?.let { PhotoThumbs.get(it) }, key) {
        if (key == null || projectId == null) { value = null; return@produceState }
        if (cached) PhotoThumbs.get(key)?.let { value = it; return@produceState }
        value = platform.projects.readFile(projectId, name)?.let(platform::decodeImage)?.also { if (cached) PhotoThumbs.put(key, it) }
    }
    return img
}

/**
 * Scheda Foto della stanza: foto del sopralluogo da riguardare mentre si disegna (dov'è la presa, da che parte apre la porta,
 * il metro appoggiato al muro). Sono solo un promemoria: non cambiano la pianta né il 3D.
 */
@Composable
internal fun PhotosTab(room: Room, vm: EditorViewModel) {
    val platform = LocalPlatform.current
    val projectId = LocalProjectId.current ?: return
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<RoomPhoto?>(null) }

    fun add(file: PickedFile?) {
        if (file == null) return
        loading = true
        scope.launch {
            val big = platform.importPhoto(file.bytes, PHOTO_SIDE, PHOTO_QUALITY)
            val mini = big?.let { platform.importPhoto(file.bytes, THUMB_SIDE, THUMB_QUALITY) }
            val stamp = platform.now()
            val photo = RoomPhoto("${RoomPhoto.PREFIX}$stamp.jpg", "${RoomPhoto.PREFIX}$stamp-mini.jpg", taken = stamp)
            val saved = big != null && mini != null && runCatching {
                platform.projects.writeFile(projectId, photo.file, big.jpeg)
                platform.projects.writeFile(projectId, photo.thumb, mini.jpeg)
            }.isSuccess
            loading = false
            if (!saved) {
                // Letta ma non salvata: di solito manca spazio (nel browser c'è un limite di pochi MB).
                platform.toast(if (big == null || mini == null) "Impossibile leggere la foto" else "Non c'è spazio per salvare la foto")
                return@launch
            }
            vm.addRoomPhoto(room.id, photo)
        }
    }

    // La spiegazione solo finché non ci sono foto: dopo, sul telefono, le miniature stanno subito sotto i pulsanti.
    if (room.photos.isEmpty()) Text(
        "Foto del sopralluogo da riguardare mentre disegni: dov'è la presa, da che parte apre la porta, il metro appoggiato al muro. " +
            "Restano nel progetto; non cambiano la pianta né il 3D.",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (platform.canTakePhoto) Button(enabled = !loading, onClick = { platform.takePhoto(::add) }) { Text("📷 Scatta") }
        val pick = if (platform.canTakePhoto) "🖼 Dalla galleria" else "📷 Aggiungi foto"
        val onPick = { platform.openFile(listOf("image/*")) { add(it) } }
        if (platform.canTakePhoto) OutlinedButton(enabled = !loading, onClick = onPick) { Text(pick) }
        else Button(enabled = !loading, onClick = onPick) { Text(pick) }
    }
    if (loading) Text("Preparo la foto…", style = MaterialTheme.typography.bodySmall)

    // Due per riga: abbastanza grandi da riconoscere la parete anche sul telefono.
    for (row in room.photos.chunked(2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (photo in row) PhotoCell(photo, Modifier.weight(1f)) { open = photo }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }

    open?.let { p -> room.photos.firstOrNull { it.file == p.file } }?.let { photo ->
        PhotoDialog(
            photo,
            onNote = { vm.setRoomPhotoNote(room.id, photo.file, it) },
            onRemove = { vm.removeRoomPhoto(room.id, photo.file); open = null },
            onDismiss = { open = null },
        )
    }
}

/** Miniatura con la nota sotto; un tocco la apre in grande. */
@Composable
private fun PhotoCell(photo: RoomPhoto, modifier: Modifier, onClick: () -> Unit) {
    val img = rememberProjectImage(photo.thumb, cached = true)
    Column(modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val shape = RoundedCornerShape(8.dp)
        val frame = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
        if (img != null) Image(img, photo.note.ifBlank { "Foto della stanza" }, frame, contentScale = ContentScale.Crop)
        else Box(frame)
        Text(
            photo.note.ifBlank { "Tocca per aggiungere una nota" },
            style = MaterialTheme.typography.bodySmall,
            color = if (photo.note.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Foto in grande, con la nota da scrivere o correggere e "Togli foto". La nota si salva alla chiusura. */
@Composable
private fun PhotoDialog(photo: RoomPhoto, onNote: (String) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val img = rememberProjectImage(photo.file, cached = false)
    var note by remember(photo.file) { mutableStateOf(photo.note) }
    val close = {
        if (note.trim() != photo.note) onNote(note)
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = close,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val frame = Modifier.fillMaxWidth().heightIn(max = 560.dp)
                if (img != null) {
                    Image(img, "Foto della stanza", frame.aspectRatio(img.width.toFloat() / img.height), contentScale = ContentScale.Fit)
                } else Box(frame.aspectRatio(4f / 3f).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text("Apro la foto…", style = MaterialTheme.typography.bodySmall)
                }
                SelectAllTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "Nota (per esempio: presa a 30 cm da terra)",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Chiudi") } },
        dismissButton = { TextButton(onClick = onRemove) { Text("Togli foto", color = MaterialTheme.colorScheme.error) } },
        modifier = Modifier.padding(8.dp),
    )
}
