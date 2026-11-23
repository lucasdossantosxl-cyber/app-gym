package br.com.meutreino

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val AppColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFA8F5BC),
    onPrimary = Color(0xFF0B2E1B),
    primaryContainer = Color(0xFF1F4D34),
    onPrimaryContainer = Color(0xFFC4FAD3),
    secondary = Color(0xFF9FD0B1),
    onSecondary = Color(0xFF0B2E1B),
    secondaryContainer = Color(0xFF243B2F),
    onSecondaryContainer = Color(0xFFCDEBD8),
    tertiary = Color(0xFFFFD27A),
    onTertiary = Color(0xFF3B2A00),
    tertiaryContainer = Color(0xFF4A3A12),
    onTertiaryContainer = Color(0xFFFFE2A8),
    background = Color(0xFF0E1512),
    onBackground = Color(0xFFE4EDE7),
    surface = Color(0xFF0E1512),
    onSurface = Color(0xFFE4EDE7),
    surfaceVariant = Color(0xFF24332C),
    onSurfaceVariant = Color(0xFFA9B8AF),
    surfaceContainerLowest = Color(0xFF0A100D),
    surfaceContainerLow = Color(0xFF131B17),
    surfaceContainer = Color(0xFF17211C),
    surfaceContainerHigh = Color(0xFF1D2A24),
    surfaceContainerHighest = Color(0xFF24332C),
    outline = Color(0xFF6B7D73),
    outlineVariant = Color(0xFF2E3D36),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1A16),
    onErrorContainer = Color(0xFFFFDAD6)
)

val PtBr: Locale = Locale.forLanguageTag("pt-BR")
private val dayFormat = DateTimeFormatter.ofPattern("EEE, d 'de' MMM", PtBr)
private val dayYearFormat = DateTimeFormatter.ofPattern("EEE, d 'de' MMM 'de' yyyy", PtBr)
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", PtBr)

fun Long.localDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

/** "sex., 21 de nov." no ano corrente; com o ano nos demais. */
fun Long.dayLabel(): String = localDate().let {
    (if (it.year == LocalDate.now().year) dayFormat else dayYearFormat).format(it)
}

fun Long.timeLabel(): String = timeFormat.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

private val photos = LruCache<String, ImageBitmap>(120)

/** Foto do exercício da biblioteca. Sem foto, mostra a inicial do nome. */
@Composable
fun ExercisePhoto(ref: String, name: String, modifier: Modifier = Modifier, frame: Int = 0, thumb: Boolean = true) {
    val context = LocalContext.current
    val file = Photos.byRef[ref].orEmpty()
    val key = "$file/$frame/$thumb"
    var bitmap by remember(key) { mutableStateOf(if (file.isEmpty()) null else photos.get(key)) }
    if (bitmap == null && file.isNotEmpty()) {
        LaunchedEffect(key) {
            bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open("ex/${file}_$frame.jpg").use { stream ->
                        BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                            inSampleSize = if (thumb) 2 else 1
                            inPreferredConfig = Bitmap.Config.RGB_565
                        })
                    }?.asImageBitmap()
                }.getOrNull()
            }?.also { photos.put(key, it) }
        }
    }
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(name.trim().take(1).uppercase(), style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

/** Campo numérico compacto. O valor sugerido aparece apagado enquanto o campo está vazio. */
@Composable
fun NumberBox(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    highlighted: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = { text ->
            onChange(text.filter { it.isDigit() || (decimal && (it == ',' || it == '.')) }.take(7))
        },
        modifier = modifier.height(46.dp)
            .background(if (highlighted) colors.primaryContainer else colors.surfaceContainerHighest, RoundedCornerShape(12.dp)),
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(
            color = if (highlighted) colors.onPrimaryContainer else colors.onSurface,
            textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number, imeAction = ImeAction.Next),
        cursorBrush = SolidColor(colors.primary),
        decorationBox = { field ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant.copy(alpha = 0.55f), textAlign = TextAlign.Center)
                field()
            }
        }
    )
}

@Composable
fun Field(
    label: String, value: String, update: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth(),
    keyboard: KeyboardType = KeyboardType.Text, supporting: String? = null
) {
    OutlinedTextField(value = value, onValueChange = update, modifier = modifier, label = { Text(label) },
        singleLine = true, supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard,
            capitalization = if (keyboard == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None))
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun Confirm(
    title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
    dismiss: String = "Cancelar", destructive: Boolean = true
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } })
}

fun <T> List<T>.replacing(index: Int, value: T): List<T> = toMutableList().apply { set(index, value) }
fun <T> List<T>.removing(index: Int): List<T> = toMutableList().apply { removeAt(index) }
fun <T> List<T>.moving(from: Int, to: Int): List<T> =
    if (to !in indices || from !in indices) this else toMutableList().apply { add(to, removeAt(from)) }
