package io.github.ambroal.climer.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Attachment(val path: String, val name: String, val mime: String?)

/** Copia i documenti nello spazio privato dell'app: restano apribili anche se l'originale viene spostato o cancellato. */
@Singleton
class AttachmentStore @Inject constructor(private val context: Context) {
    private val root get() = File(context.filesDir, "attachments")

    suspend fun import(programId: Long, uri: Uri): Attachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "documento"
        val dir = File(root, programId.toString()).apply {
            deleteRecursively()
            mkdirs()
        }
        val target = File(dir, name.replace(Regex("""[\\/:*?"<>|]"""), "_"))
        resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: error("Impossibile leggere il documento")
        Attachment(target.relativeTo(context.filesDir).path, name, resolver.getType(uri))
    }

    /** Null se il file non c'è più (es. dati importati da un altro telefono). */
    fun uriFor(path: String): Uri? {
        val file = File(context.filesDir, path)
        return if (file.exists()) FileProvider.getUriForFile(context, "${context.packageName}.files", file) else null
    }

    suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        File(context.filesDir, path).parentFile?.deleteRecursively()
    }
}
