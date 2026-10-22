@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.tvhome.launcher

import android.content.Context
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.media.tv.TvView
import android.view.KeyEvent
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// ---------------------------------------------------------------------------------------------
// Datos y fuentes: antena (TvProvider del sistema) + IPTV (lista M3U)
// ---------------------------------------------------------------------------------------------

@Immutable
class TvChannel(
    val key: String,
    val name: String,
    val number: String?,
    val antenna: Boolean,
    val inputId: String?,   // solo antena
    val channelId: Long,    // solo antena
    val url: String?,       // solo IPTV
    val group: String?,
    val logo: String?,      // URL del logo (IPTV)
    val category: String? = null,   // "Deportes" para la lista de deportes
)

/** Una lista M3U de IPTV. La categoría (p. ej. "Deportes") sirve para separarla con un filtro. */
class IptvSource(val id: String, val label: String, val url: String, val category: String?)

/**
 * País de la lista de iptv-org que se carga (código de 2 letras: mx, us, es, ar, co, cl, pe…).
 * Todos los códigos: https://iptv-org.github.io/iptv/countries/
 */
const val IPTV_COUNTRY = "mx"

/** Listas que se cargan por defecto. Para añadir otra sin tocar el código: adb ... --es iptv_url "URL". */
val DEFAULT_SOURCES = listOf(
    IptvSource(
        "country_$IPTV_COUNTRY", "IPTV-Org ${IPTV_COUNTRY.uppercase()}",
        "https://iptv-org.github.io/iptv/countries/$IPTV_COUNTRY.m3u", null,
    ),
    IptvSource("sports", "Deportes", "https://iptv-org.github.io/iptv/categories/sports.m3u", "Deportes"),
)

object ChannelSources {

    const val TV_PERM = "android.permission.READ_TV_LISTINGS"

    /** Canales que la TV ya escaneó de la antena. Necesita el permiso READ_TV_LISTINGS. */
    fun antenna(ctx: Context): List<TvChannel> = runCatching {
        val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
        val tuners = tim.tvInputList.filter { it.type == TvInputInfo.TYPE_TUNER }.map { it.id }.toSet()
        val proj = arrayOf(
            TvContract.Channels._ID,
            TvContract.Channels.COLUMN_DISPLAY_NUMBER,
            TvContract.Channels.COLUMN_DISPLAY_NAME,
            TvContract.Channels.COLUMN_INPUT_ID,
            TvContract.Channels.COLUMN_TYPE,
        )
        val out = ArrayList<TvChannel>()
        val seen = HashSet<String>()
        ctx.contentResolver.query(TvContract.Channels.CONTENT_URI, proj, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val number = c.getString(1)
                val name = c.getString(2).orEmpty().ifBlank { "Canal ${number ?: id}" }
                val input = c.getString(3) ?: continue
                val type = c.getString(4)
                if (type == TvContract.Channels.TYPE_PREVIEW) continue          // recomendaciones de apps
                if (tuners.isNotEmpty() && input !in tuners) continue            // solo el sintonizador
                if (!seen.add("$input/$id")) continue
                out += TvChannel("a$id", name, number, true, input, id, null, "Antena", null)
            }
        }
        out.sortedWith(compareBy({ numberKey(it.number) }, { it.name.lowercase() }))
    }.getOrDefault(emptyList())

    private fun numberKey(n: String?): Double {
        if (n.isNullOrBlank()) return Double.MAX_VALUE
        val parts = n.split('.', '-')
        val a = parts.getOrNull(0)?.toDoubleOrNull() ?: return Double.MAX_VALUE
        val b = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        return a + b / 1000.0
    }

    private val ATTR = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun parseM3u(text: String, src: IptvSource): List<TvChannel> {
        val out = ArrayList<TvChannel>()
        var name: String? = null
        var attrs: Map<String, String> = emptyMap()
        var n = 0
        for (raw in text.lineSequence()) {
            val l = raw.trim()
            if (l.isEmpty()) continue
            if (l.startsWith("#EXTINF", ignoreCase = true)) {
                attrs = ATTR.findAll(l).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                name = l.substringAfterLast(',').trim().ifBlank { attrs["tvg-name"].orEmpty() }
            } else if (!l.startsWith("#")) {
                if (name != null && (l.startsWith("http://") || l.startsWith("https://"))) {
                    n++
                    out += TvChannel(
                        key = "${src.id}_$n",
                        name = name.ifBlank { "Canal $n" },
                        number = attrs["tvg-chno"],
                        antenna = false,
                        inputId = null,
                        channelId = 0L,
                        url = l,
                        group = attrs["group-title"],
                        logo = attrs["tvg-logo"]?.takeIf { it.startsWith("http") },
                        category = src.category,
                    )
                }
                name = null
                attrs = emptyMap()
            }
        }
        return out
    }

    fun download(url0: String): String {
        var url = url0
        repeat(5) {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11) TVHome")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IOException("redirección sin destino")
                url = URL(URL(url), loc).toString()
                c.disconnect()
                return@repeat
            }
            if (code != 200) {
                c.disconnect()
                throw IOException("HTTP $code")
            }
            return c.inputStream.bufferedReader().use { it.readText() }
        }
        throw IOException("demasiadas redirecciones")
    }
}

// ---------------------------------------------------------------------------------------------
// Logos remotos (sin Coil): se descargan al verse y se guardan en memoria
// ---------------------------------------------------------------------------------------------

private val logoCache = android.util.LruCache<String, ImageBitmap>(120)

@Composable
private fun rememberLogo(url: String?): ImageBitmap? {
    val st = produceState<ImageBitmap?>(initialValue = url?.let { logoCache.get(it) }, url) {
        if (url != null && value == null) {
            val img = withContext(Dispatchers.IO) { loadLogo(url) }
            if (img != null) logoCache.put(url, img)
            value = img
        }
    }
    return st.value
}

private fun loadLogo(url: String): ImageBitmap? = runCatching {
    val conn = URL(url).openConnection().apply { connectTimeout = 8_000; readTimeout = 8_000 }
    val bytes = conn.getInputStream().use { it.readBytes() }
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    var sample = 1
    while (o.outHeight / (sample * 2) >= 128) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
}.getOrNull()

// ---------------------------------------------------------------------------------------------
// Pantalla de canales
// ---------------------------------------------------------------------------------------------

private val NEON_ANT = Color(0xFF7CC4FF)
private val NEON_IPTV = Color(0xFFFF6FB5)
private val NEON_SPORT = Color(0xFF2BE38B)
private val RED = Color(0xFFFF6B6B)

private fun neonOf(c: TvChannel): Color = when {
    c.antenna -> NEON_ANT
    c.category != null -> NEON_SPORT
    else -> NEON_IPTV
}

private const val ST_LOADING = 0
private const val ST_PLAYING = 1
private const val ST_ERROR = 2

private val HEADER_H = 64.dp   // alto del título (columna derecha)

@Composable
fun ChannelsScreen(
    channels: List<TvChannel>,
    status: String,
    loading: Boolean,
    initialKey: String?,
    onCurrent: (String) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var filter by remember { mutableIntStateOf(0) }        // 0 todos, 1 antena, 2 IPTV, 3 deportes
    var curKey by remember { mutableStateOf(initialKey) }
    var full by remember { mutableStateOf(false) }
    val visible = remember(channels, filter) {
        when (filter) {
            1 -> channels.filter { it.antenna }
            2 -> channels.filter { !it.antenna && it.category == null }
            3 -> channels.filter { it.category != null }
            else -> channels
        }
    }
    val current = remember(channels, curKey) { channels.firstOrNull { it.key == curKey } }
    // fila que recibe el foco: el canal actual si está en el filtro; si no, el primero
    val focusKey = visible.firstOrNull { it.key == curKey }?.key ?: visible.firstOrNull()?.key

    var state by remember(curKey) { mutableIntStateOf(ST_LOADING) }
    var note by remember(curKey) { mutableStateOf<String?>(null) }
    var showInfo by remember(curKey, full) { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }

    val videoFr = remember { FocusRequester() }
    val rowFr = remember { FocusRequester() }
    val listState = rememberLazyListState()

    fun play(c: TvChannel) {
        curKey = c.key
        onCurrent(c.key)
    }

    fun step(d: Int) {
        if (visible.isEmpty()) return
        val i = visible.indexOfFirst { it.key == curKey }
        val n = if (i < 0) 0 else (i + d + visible.size) % visible.size
        play(visible[n])
    }

    BackHandler(enabled = full) { full = false }

    // si un canal se queda cargando demasiado, se marca fuera de servicio
    LaunchedEffect(curKey, state, full, retry) {
        val c = current
        if (c != null && state == ST_LOADING) {
            delay(if (c.antenna) 12_000L else 20_000L)
            state = ST_ERROR
            note = null
        }
    }
    // la antena cambia de sesión al entrar o salir de pantalla completa: se empieza de cero
    LaunchedEffect(full) {
        if (current?.antenna == true) {
            state = ST_LOADING
            note = null
        }
    }
    LaunchedEffect(curKey, full, showInfo) {
        if (full && showInfo) {
            delay(3500)
            showInfo = false
        }
    }
    LaunchedEffect(curKey) {
        if (!full) {
            val i = visible.indexOfFirst { it.key == curKey }
            if (i >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == i }) listState.animateScrollToItem(i)
        }
    }
    // foco: en pantalla completa en el video; en la vista dividida, en el canal actual de la lista
    LaunchedEffect(full, visible.isNotEmpty()) {
        if (full) {
            delay(60)
            runCatching { videoFr.requestFocus() }
        } else if (visible.isNotEmpty()) {
            val i = visible.indexOfFirst { it.key == focusKey }.coerceAtLeast(0)
            listState.scrollToItem(i)
            delay(80)
            runCatching { rowFr.requestFocus() }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { e ->
                // CH+ / CH- del control funcionan estés donde estés en esta pantalla
                if (e.type != KeyEventType.KeyDown) {
                    false
                } else when (e.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_CHANNEL_UP -> { step(1); true }
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> { step(-1); true }
                    else -> false
                }
            }
    ) {
        val vw = maxWidth * 0.54f
        val vh = vw * 9f / 16f
        val listW = maxWidth * 0.36f
        if (!full) Wallpaper()

        // --- video: siempre en la misma posición del árbol, así no se reinicia al pasar a pantalla completa
        VideoPane(
            modifier = if (full) Modifier.fillMaxSize()
            else Modifier.size(vw, vh),   // en (0,0): la TV dibuja el video de antena desde la esquina, sin respetar márgenes
            full = full,
            current = current,
            state = state,
            note = note,
            showInfo = showInfo,
            focus = videoFr,
            onState = { s, n -> state = s; note = n },
            retry = retry,
            onClick = {
                when {
                    state == ST_ERROR -> {
                        retry++
                        state = ST_LOADING
                        note = null
                    }
                    full -> showInfo = !showInfo
                    current != null -> full = true
                }
            },
            onKey = { code ->
                when (code) {
                    KeyEvent.KEYCODE_DPAD_UP -> if (full) { step(1); true } else false
                    KeyEvent.KEYCODE_DPAD_DOWN -> if (full) { step(-1); true } else false
                    else -> false
                }
            },
        )

        if (!full) {
            // --- título e iconos, arriba a la derecha
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = EDGE)
                    .width(listW)
                    .height(HEADER_H),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph("tv", Color.White, Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Canales",
                    fontFamily = Poppins, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = cs.onBackground,
                )
                Spacer(Modifier.weight(1f))
                IconPill("refresh", Color.White) { onRefresh() }
                Spacer(Modifier.width(8.dp))
                IconPill("back", Color.White) { onClose() }
            }

            // --- columna izquierda, debajo del video: filtros y datos del canal actual
            Column(
                Modifier
                    .padding(start = EDGE, top = vh + 12.dp)
                    .width(vw - EDGE),
            ) {
                val nAnt = channels.count { it.antenna }
                val nSport = channels.count { it.category != null }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip("Todos ${channels.size}", filter == 0) { filter = 0 }
                    Spacer(Modifier.width(6.dp))
                    FilterChip("Antena $nAnt", filter == 1) { filter = 1 }
                    Spacer(Modifier.width(6.dp))
                    FilterChip("IPTV ${channels.size - nAnt - nSport}", filter == 2) { filter = 2 }
                    Spacer(Modifier.width(6.dp))
                    FilterChip("Deportes $nSport", filter == 3) { filter = 3 }
                }
                Spacer(Modifier.height(12.dp))
                if (current != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!current.number.isNullOrBlank()) {
                            Text(
                                current.number,
                                fontFamily = Poppins, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                color = neonOf(current),
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            current.name,
                            fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        if (current.antenna) "Antena" else (current.group ?: current.category ?: "IPTV"),
                        fontFamily = Poppins, fontSize = 12.sp, color = neonOf(current), maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "OK sobre el video: pantalla completa",
                        fontFamily = Poppins, fontSize = 11.sp, color = cs.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Elige un canal de la lista",
                        fontFamily = Poppins, fontSize = 14.sp, color = cs.onSurfaceVariant,
                    )
                }
                val line = if (loading) "Cargando lista de canales…" else status
                if (line.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        line,
                        fontFamily = Poppins, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 4,
                    )
                }
            }

            // --- lista a la derecha
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = EDGE, top = HEADER_H)
                    .width(listW)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(visible, key = { _, c -> c.key }) { _, c ->
                    ChannelRow(
                        c,
                        selected = c.key == curKey,
                        modifier = if (c.key == focusKey) Modifier.focusRequester(rowFr) else Modifier,
                    ) { play(c) }
                }
            }
        }
    }
}

/** Panel de video: vista previa a la izquierda o pantalla completa. Estado, carga y "fuera de servicio" van encima. */
@Composable
private fun VideoPane(
    modifier: Modifier,
    full: Boolean,
    current: TvChannel?,
    state: Int,
    note: String?,
    showInfo: Boolean,
    focus: FocusRequester,
    retry: Int,
    onState: (Int, String?) -> Unit,
    onClick: () -> Unit,
    onKey: (Int) -> Boolean,
) {
    val neon = if (current != null) neonOf(current) else NEON_ANT
    var focused by remember { mutableStateOf(false) }
    // Box plano: el SurfaceView no soporta bien recortes redondeados ni escala (el video se pierde)
    Box(
        modifier
            .focusRequester(focus)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) {
                    false
                } else {
                    val code = e.nativeKeyEvent.keyCode
                    if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                        onClick()
                        true
                    } else {
                        onKey(code)
                    }
                }
            }
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        run {
            if (current == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Glyph("tv", Color(0x66FFFFFF), Modifier.size(44.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Elige un canal",
                        fontFamily = Poppins, fontSize = 14.sp, color = Color(0x99FFFFFF),
                    )
                }
            } else {
                androidx.compose.runtime.key(current.key, retry, current.antenna && full) {
                    if (current.antenna) AntennaVideo(current, onState) else IptvVideo(current.url.orEmpty(), onState)
                }

                // cargando: spinner pequeño abajo a la izquierda
                if (state == ST_LOADING) {
                    Row(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(if (full) 32.dp else 10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xB3000000))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spinner(16.dp, neon)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            note ?: "Cargando…",
                            fontFamily = Poppins, fontSize = 11.sp, color = Color.White, maxLines = 1,
                        )
                    }
                }

                // fuera de servicio
                if (state == ST_ERROR) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xE6000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Glyph("tvoff", RED, Modifier.size(if (full) 64.dp else 40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Fuera de servicio",
                                fontFamily = Poppins, fontSize = if (full) 22.sp else 16.sp,
                                fontWeight = FontWeight.Bold, color = Color.White,
                            )
                            Text(
                                "Este canal no responde. Prueba con otro.",
                                fontFamily = Poppins, fontSize = if (full) 14.sp else 11.sp,
                                color = Color(0xCCFFFFFF), textAlign = TextAlign.Center,
                            )
                            Text(
                                "Pulsa OK para reintentar",
                                fontFamily = Poppins, fontSize = if (full) 12.sp else 10.sp,
                                color = Color(0xB3FFFFFF), textAlign = TextAlign.Center,
                            )
                            if (note != null) {
                                Text(
                                    note,
                                    fontFamily = Poppins, fontSize = 9.sp,
                                    color = Color(0x80FFFFFF), textAlign = TextAlign.Center, maxLines = 1,
                                )
                            }
                        }
                    }
                }

                // pantalla completa: nombre del canal unos segundos
                if (full && showInfo) {
                    Row(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(EDGE)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xB3000000))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!current.number.isNullOrBlank()) {
                            Text(
                                current.number,
                                fontFamily = Poppins, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = neon,
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column {
                            Text(
                                current.name,
                                fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                color = Color.White, maxLines = 1,
                            )
                            Text(
                                if (current.antenna) "Antena" else (current.group ?: current.category ?: "IPTV"),
                                fontFamily = Poppins, fontSize = 12.sp, color = neon, maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        // borde neón encima del video (se dibuja después, así no lo tapa)
        if (!full) {
            Box(
                Modifier
                    .fillMaxSize()
                    .border(
                        if (focused) 3.dp else 1.5.dp,
                        neon.copy(alpha = if (focused) 1f else 0.35f),
                        RectangleShape,
                    )
            )
        }
    }
}

@Composable
private fun Spinner(size: Dp, color: Color) {
    val t = rememberInfiniteTransition(label = "spin")
    val a by t.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "angle",
    )
    Canvas(Modifier.size(size)) {
        val sw = this.size.width * 0.14f
        val inset = sw / 2
        val box = Size(this.size.width - sw, this.size.height - sw)
        drawArc(color.copy(alpha = 0.25f), 0f, 360f, false, Offset(inset, inset), box, style = Stroke(sw))
        drawArc(color, a, 100f, false, Offset(inset, inset), box, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val dark = Color(0xFF1B1B1F)
    Surface(
        onClick = onClick,
        modifier = Modifier.height(32.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White else Color(0xB34A4458),
            contentColor = if (selected) dark else cs.onSurface,
            focusedContainerColor = Color.White,
            focusedContentColor = dark,
            pressedContainerColor = Color.White,
            pressedContentColor = dark,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = Color.White.copy(alpha = 0.55f), elevation = 12.dp),
        ),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, fontFamily = Poppins, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

/** Fila de la lista: número o logo, nombre y bandera. El canal que se está viendo lleva el neón más fuerte. */
@Composable
private fun ChannelRow(c: TvChannel, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val neon = neonOf(c)
    val shape = RoundedCornerShape(R_NORMAL)
    val focusShape = RoundedCornerShape(R_FOCUS)
    val logo = rememberLogo(c.logo)
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape, focusedShape = focusShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0xE6252030) else Color(0xCC1D1B22),
            focusedContainerColor = Color(0xF21D1B22),
            pressedContainerColor = Color(0xF21D1B22),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(if (selected) 2.dp else 1.5.dp, neon.copy(alpha = if (selected) 0.9f else 0.3f)), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, neon), shape = focusShape),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = neon.copy(alpha = 0.9f), elevation = GLOW),
        ),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                if (logo != null) {
                    Image(logo, null, Modifier.size(36.dp), contentScale = ContentScale.Fit)
                } else if (!c.number.isNullOrBlank()) {
                    Text(
                        c.number,
                        fontFamily = Poppins, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = neon, maxLines = 1,
                    )
                } else {
                    Glyph("tv", neon, Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    c.name,
                    fontFamily = Poppins, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White,
                )
                Text(
                    if (c.antenna) "Antena" else (c.group ?: c.category ?: "IPTV"),
                    fontFamily = Poppins, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = neon,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Reproductores: IPTV (ExoPlayer) y antena (TvView). Avisan su estado: cargando / reproduciendo / error
// ---------------------------------------------------------------------------------------------

@Composable
private fun IptvVideo(url: String, onState: (Int, String?) -> Unit) {
    val ctx = LocalContext.current
    val player = remember(url) { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> onState(ST_LOADING, null)
                    Player.STATE_READY -> onState(ST_PLAYING, null)
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("TVHome", "IPTV error: " + error.errorCodeName + " " + url, error)
                onState(ST_ERROR, error.errorCodeName)
            }
        }
        player.addListener(l)
        // aquí (y no al crear el reproductor) para que el canal anterior ya haya soltado el decodificador
        player.setMediaItem(MediaItem.fromUri(url))
        player.playWhenReady = true
        player.prepare()
        onDispose {
            player.removeListener(l)
            player.release()
        }
    }
    AndroidView(
        factory = { c ->
            SurfaceView(c).also { v ->
                v.isFocusable = false
                player.setVideoSurfaceView(v)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun AntennaVideo(ch: TvChannel, onState: (Int, String?) -> Unit) {
    val ctx = LocalContext.current
    val view = remember(ch.key) { TvView(ctx) }
    DisposableEffect(view) {
        onDispose { runCatching { view.reset() } }
    }
    // la sesión anterior tarda un momento en soltar el sintonizador; sintonizar de inmediato falla
    var ready by remember(ch.key) { mutableStateOf(false) }
    LaunchedEffect(ch.key) {
        delay(500)
        ready = true
    }
    if (ready) AndroidView(
        factory = {
            view.apply {
                isFocusable = false
                setCallback(object : TvView.TvInputCallback() {
                    override fun onConnectionFailed(inputId: String?) {
                        android.util.Log.e("TVHome", "TvView connection failed: $inputId")
                        onState(ST_ERROR, "No se pudo conectar con el sintonizador")
                    }

                    override fun onVideoAvailable(inputId: String?) {
                        onState(ST_PLAYING, null)
                    }

                    override fun onVideoUnavailable(inputId: String?, reason: Int) {
                        when (reason) {
                            TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL -> onState(ST_LOADING, "Señal débil")
                            TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING -> onState(ST_LOADING, "Sintonizando…")
                            TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING -> onState(ST_LOADING, null)
                            else -> onState(ST_LOADING, "Sin video")   // si no llega video, vence el tiempo y pasa a "fuera de servicio"
                        }
                    }
                })
                tune(ch.inputId.orEmpty(), TvContract.buildChannelUri(ch.channelId))
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
