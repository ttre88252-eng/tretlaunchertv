package com.tret.tvlauncher

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// ───────────────────────── CONFIGURACIÓN (edita aquí) ─────────────────────────
object Config {
    const val CIUDAD = "Tamazula"
    const val LAT = 19.68
    const val LON = -103.25
    const val SALUDO = "Hola"
}

// ───────────────────────── MODELOS ─────────────────────────
data class AppItem(val label: String, val pkg: String, val imagen: ImageBitmap, val esBanner: Boolean)
data class HoraClima(val temp: Int, val code: Int, val hora: Int)
data class Clima(val actual: Int, val code: Int, val max: Int, val min: Int, val horas: List<HoraClima>)
data class Continuar(val titulo: String, val detalle: String, val pkg: String, val color: Color)

// Ejemplos de "A continuación". Cámbialos por lo que quieras (o conéctalos a una API como TMDB).
val continuarEjemplo = listOf(
    Continuar("Susurros mortales", "Quedan 1 h 58 min · Netflix", "com.netflix.ninja", Color(0xFF1F5F4A)),
    Continuar("Mi película", "Quedan 1 h 27 min · Prime Video", "com.amazon.amazonvideo.livingroom", Color(0xFFE5B800)),
    Continuar("Lupin", "T1 E1 · Netflix", "com.netflix.ninja", Color(0xFF3B4A6B)),
)

// ───────────────────────── ACTIVIDAD ─────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherApp() }
    }
}

fun Context.abrirApp(pkg: String) {
    val i = packageManager.getLeanbackLaunchIntentForPackage(pkg)
        ?: packageManager.getLaunchIntentForPackage(pkg)
    i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { startActivity(it) }
}

fun Context.abrirAjustes(accion: String) {
    try {
        startActivity(Intent(accion).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { }
    }
}

fun cargarApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    var lista = pm.queryIntentActivities(intent, 0)
    if (lista.isEmpty()) {
        lista = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
    }
    return lista
        .filter { it.activityInfo.packageName != ctx.packageName }
        .distinctBy { it.activityInfo.packageName }
        .mapNotNull { ri ->
            try {
                val banner = ri.activityInfo.loadBanner(pm)
                val d = banner ?: ri.loadIcon(pm)
                AppItem(
                    label = ri.loadLabel(pm).toString(),
                    pkg = ri.activityInfo.packageName,
                    imagen = d.toBitmap(320, 180).asImageBitmap(),
                    esBanner = banner != null
                )
            } catch (e: Exception) { null }
        }
        .sortedBy { it.label.lowercase() }
}

// ───────────────────────── CLIMA (Open-Meteo, sin API key) ─────────────────────────
fun descargarClima(): Clima? = try {
    val url = URL(
        "https://api.open-meteo.com/v1/forecast?latitude=${Config.LAT}&longitude=${Config.LON}" +
            "&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=2"
    )
    val con = url.openConnection() as HttpURLConnection
    con.connectTimeout = 8000
    con.readTimeout = 8000
    val json = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
    val cur = json.getJSONObject("current")
    val h = json.getJSONObject("hourly")
    val temps = h.getJSONArray("temperature_2m")
    val codes = h.getJSONArray("weather_code")
    val d = json.getJSONObject("daily")
    val inicio = LocalTime.now().hour
    val horas = (0..4).map { i ->
        val idx = inicio + i
        HoraClima(temps.getDouble(idx).roundToInt(), codes.getInt(idx), idx % 24)
    }
    Clima(
        actual = cur.getDouble("temperature_2m").roundToInt(),
        code = cur.getInt("weather_code"),
        max = d.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
        min = d.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
        horas = horas
    )
} catch (e: Exception) { null }

fun emojiClima(code: Int, hora: Int): String {
    val noche = hora < 7 || hora >= 20
    return when (code) {
        0 -> if (noche) "🌙" else "☀️"
        1, 2 -> if (noche) "☁️" else "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..67, in 80..82 -> "🌧️"
        in 71..77, 85, 86 -> "❄️"
        in 95..99 -> "⛈️"
        else -> "⛅"
    }
}

fun textoClima(code: Int): String = when (code) {
    0 -> "Despejado"
    1, 2 -> "Poco nublado"
    3 -> "Nublado"
    45, 48 -> "Niebla"
    in 51..57 -> "Llovizna"
    in 61..67, in 80..82 -> "Lluvia"
    in 71..77, 85, 86 -> "Nieve"
    in 95..99 -> "Tormenta"
    else -> "—"
}

// ───────────────────────── UI ─────────────────────────
@Composable
fun LauncherApp() {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var clima by remember { mutableStateOf<Clima?>(null) }
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { cargarApps(ctx) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            clima = withContext(Dispatchers.IO) { descargarClima() } ?: clima
            delay(15 * 60 * 1000L)
        }
    }

    val fondo = Brush.verticalGradient(
        listOf(Color(0xFF1E3AA8), Color(0xFF5A3FB0), Color(0xFFB5559F), Color(0xFF2A2E8C))
    )

    Box(Modifier.fillMaxSize().background(fondo)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
            BarraSuperior(tab) { tab = it }
            Spacer(Modifier.height(16.dp))
            when (tab) {
                0 -> Inicio(apps, clima)
                1 -> TodasLasApps(apps)
                else -> Ajustes()
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BarraSuperior(tab: Int, onTab: (Int) -> Unit) {
    var hora by remember { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    LaunchedEffect(Unit) {
        while (true) {
            hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            delay(1000)
        }
    }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.align(Alignment.Center)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Inicio", "Apps", "Ajustes").forEachIndexed { i, nombre ->
                Surface(
                    onClick = { onTab(i) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(22.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (tab == i) Color.White.copy(alpha = 0.28f) else Color.Transparent,
                        focusedContainerColor = Color.White,
                        contentColor = Color.White,
                        focusedContentColor = Color(0xFF1E2A78)
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f)
                ) {
                    Text(nombre, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
                }
            }
        }
        Text(
            hora, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
fun Inicio(apps: List<AppItem>, clima: Clima?) {
    val ctx = LocalContext.current
    val fecha = LocalDate.now()
        .format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "MX")))
        .replaceFirstChar { it.uppercase() }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(24.dp))
        Text(Config.SALUDO, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold)
        Text(fecha, color = Color.White.copy(alpha = 0.9f), fontSize = 24.sp)
        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth().height(250.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // ── A continuación ──
            TarjetaVidrio(Modifier.weight(2f)) {
                Text("A continuación", color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(continuarEjemplo) { c ->
                        Column(Modifier.width(210.dp)) {
                            Surface(
                                onClick = { ctx.abrirApp(c.pkg) },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                                colors = ClickableSurfaceDefaults.colors(containerColor = c.color, focusedContainerColor = c.color),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                                modifier = Modifier.fillMaxWidth().height(118.dp)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(c.titulo, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(c.titulo, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(c.detalle, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
            // ── Clima ──
            TarjetaVidrio(Modifier.weight(1f)) { TarjetaClima(clima) }
        }

        Spacer(Modifier.height(24.dp))
        FilaApps(apps.take(12))
    }
}

@Composable
fun TarjetaClima(clima: Clima?) {
    Text("➤ ${Config.CIUDAD}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    if (clima == null) {
        Spacer(Modifier.height(12.dp))
        Text("Cargando clima…", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${clima.actual}°", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light)
        Column(horizontalAlignment = Alignment.End) {
            Text(emojiClima(clima.code, LocalTime.now().hour), fontSize = 28.sp)
            Text(textoClima(clima.code), color = Color.White, fontSize = 14.sp)
            Text("H:${clima.max}°  L:${clima.min}°", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
        }
    }
    Spacer(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        clima.horas.forEachIndexed { i, h ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (i == 0) "Ahora" else "%02d".format(h.hora), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                Text(emojiClima(h.code, h.hora), fontSize = 18.sp)
                Text("${h.temp}°", color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun TarjetaVidrio(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    val forma = RoundedCornerShape(28.dp)
    Column(
        modifier
            .fillMaxSize()
            .clip(forma)
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), forma)
            .padding(18.dp),
        content = contenido
    )
}

@Composable
fun FilaApps(apps: List<AppItem>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        items(apps) { app -> TarjetaApp(app) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TarjetaApp(app: AppItem) {
    val ctx = LocalContext.current
    var enfocada by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = { ctx.abrirApp(app.pkg) },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.18f),
                focusedContainerColor = Color.White.copy(alpha = 0.95f)
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
            modifier = Modifier.size(width = 176.dp, height = 100.dp).onFocusChanged { enfocada = it.isFocused }
        ) {
            Image(
                bitmap = app.imagen,
                contentDescription = app.label,
                contentScale = if (app.esBanner) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(if (app.esBanner) 0.dp else 12.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (enfocada) app.label else " ",
            color = Color.White, fontSize = 14.sp, maxLines = 1
        )
    }
}

@Composable
fun TodasLasApps(apps: List<AppItem>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().padding(top = 12.dp)
    ) {
        items(apps) { app -> TarjetaApp(app) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun Ajustes() {
    val ctx = LocalContext.current
    val opciones = listOf(
        "Ajustes del sistema" to Settings.ACTION_SETTINGS,
        "Wi-Fi / Red" to Settings.ACTION_WIFI_SETTINGS,
        "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "Cambiar launcher predeterminado" to Settings.ACTION_HOME_SETTINGS,
    )
    Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        opciones.forEach { (nombre, accion) ->
            Surface(
                onClick = { ctx.abrirAjustes(accion) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.16f),
                    focusedContainerColor = Color.White,
                    contentColor = Color.White,
                    focusedContentColor = Color(0xFF1E2A78)
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                modifier = Modifier.width(420.dp)
            ) {
                Text(nombre, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
            }
        }
    }
}
