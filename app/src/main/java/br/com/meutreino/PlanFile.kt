package br.com.meutreino

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream

class PlanFileException(message: String) : Exception(message)

/** Lê uma ficha de treino em Markdown, texto ou PDF. Tudo acontece no aparelho. */
object PlanFile {
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val MAX_PAGES = 40

    fun name(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "arquivo"

    fun read(context: Context, uri: Uri): String {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    if (output.size() > MAX_BYTES) throw PlanFileException("O arquivo passa de 8 MB. Envie só a ficha de treino.")
                }
                output.toByteArray()
            }
        } catch (error: PlanFileException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: throw PlanFileException("Não foi possível abrir o arquivo.")
        return text(context, bytes)
    }

    fun text(context: Context, bytes: ByteArray): String {
        if (bytes.size >= 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()) return pdf(context, bytes)
        if (bytes.take(4096).count { it == 0.toByte() } > 4) {
            throw PlanFileException("Esse arquivo não é texto nem PDF. Use .md, .txt ou .pdf.")
        }
        return String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
    }

    private fun pdf(context: Context, bytes: ByteArray): String = try {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(bytes).use { document ->
            if (document.isEncrypted) throw PlanFileException("O PDF está protegido por senha.")
            val text = PDFTextStripper().apply {
                sortByPosition = true
                endPage = MAX_PAGES
            }.getText(document)
            if (text.isBlank()) {
                throw PlanFileException("O PDF não tem texto selecionável (parece uma foto ou digitalização).")
            }
            text
        }
    } catch (error: PlanFileException) {
        throw error
    } catch (_: Throwable) {
        throw PlanFileException("Não foi possível ler o PDF.")
    }
}
