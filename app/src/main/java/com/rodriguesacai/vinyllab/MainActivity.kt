package com.rodriguesacai.vinyllab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rodriguesacai.vinyllab.ui.theme.VinylLabTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToLong

class MainActivity : ComponentActivity() {
    private val playerViewModel: PlayerViewModel by viewModels()

    private val pickAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(playerViewModel::loadTrack)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VinylLabTheme {
                VinylLabScreen(
                    viewModel = playerViewModel,
                    onSelectMusic = { pickAudio.launch(arrayOf("audio/*")) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VinylLabScreen(
    viewModel: PlayerViewModel,
    onSelectMusic: () -> Unit
) {
    val state = viewModel.uiState
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var deckRotation by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.isPlaying, state.reverseJogEnabled, state.speed) {
        while (state.isPlaying || state.reverseJogEnabled) {
            val direction = if (state.reverseJogEnabled) -1f else 1f
            deckRotation = (deckRotation + direction * (4f * state.speed)) % 360f
            delay(16)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090A16))
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Header()
            Spacer(Modifier.height(14.dp))

            TrackCard(
                trackName = state.trackName,
                hasTrack = state.hasTrack,
                onSelectMusic = onSelectMusic
            )

            Spacer(Modifier.height(18.dp))

            VinylDeck(
                rotationDegrees = deckRotation,
                isPlaying = state.isPlaying,
                onRotate = { delta -> deckRotation += delta },
                onScratchStart = viewModel::startScratch,
                onScratch = { degrees ->
                    // A volta completa representa aproximadamente 7 segundos de navegação.
                    viewModel.jogBy((degrees * 19f).roundToLong())
                },
                onScratchEnd = viewModel::endScratch
            )

            Spacer(Modifier.height(14.dp))
            Text(
                text = if (state.reverseJogEnabled) "JOG REVERSO ATIVO" else if (state.isPlaying) "TOCANDO" else "PRONTO PARA TOCAR",
                color = if (state.reverseJogEnabled) Color(0xFFFFA7A0) else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                letterSpacing = 1.4.sp
            )

            Spacer(Modifier.height(14.dp))
            TransportControls(
                isPlaying = state.isPlaying,
                reverseJogEnabled = state.reverseJogEnabled,
                hasTrack = state.hasTrack,
                onPlayPause = viewModel::togglePlayPause,
                onReverseJog = viewModel::toggleReverseJog,
                onJogBack = { viewModel.jogBy(-5_000L) },
                onJogForward = { viewModel.jogBy(5_000L) }
            )

            Spacer(Modifier.height(16.dp))
            ProgressControl(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = viewModel::seekTo
            )

            Spacer(Modifier.height(10.dp))
            SpeedAndPitch(
                speed = state.speed,
                pitch = state.pitch,
                onSpeed = viewModel::setSpeed,
                onPitch = viewModel::setPitch
            )

            Spacer(Modifier.height(16.dp))
            LoopControls(
                enabled = state.loopEnabled,
                startMs = state.loopStartMs,
                endMs = state.loopEndMs,
                onToggle = viewModel::toggleLoop
            )

            Spacer(Modifier.height(18.dp))
            InfoCard()
            Spacer(Modifier.height(30.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "VINYL LAB",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Black,
                fontSize = 25.sp,
                letterSpacing = 1.8.sp
            )
            Text(
                text = "PLAYER • SCRATCH • JOG",
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp
            )
        }
        Text(
            text = "v1.0.0",
            color = MaterialTheme.colorScheme.outline,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun TrackCard(trackName: String, hasTrack: Boolean, onSelectMusic: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13152A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("♫", color = Color(0xFF171717), fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasTrack) "FAIXA CARREGADA" else "BIBLIOTECA LOCAL",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = trackName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onSelectMusic) {
                Text(if (hasTrack) "TROCAR" else "ESCOLHER", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun VinylDeck(
    rotationDegrees: Float,
    isPlaying: Boolean,
    onRotate: (Float) -> Unit,
    onScratchStart: () -> Unit,
    onScratch: (Float) -> Unit,
    onScratchEnd: () -> Unit
) {
    var previousAngle by remember { mutableFloatStateOf(0f) }
    var scratching by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.84f)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { point ->
                            previousAngle = angleFor(point, size)
                            scratching = true
                            onScratchStart()
                        },
                        onDragEnd = {
                            scratching = false
                            onScratchEnd()
                        },
                        onDragCancel = {
                            scratching = false
                            onScratchEnd()
                        }
                    ) { change, _ ->
                        change.consume()
                        val current = angleFor(change.position, size)
                        val delta = normalizedAngle(current - previousAngle)
                        previousAngle = current
                        onRotate(delta)
                        onScratch(delta)
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            drawCircle(Color(0xFF05050A), radius, center)
            drawCircle(Color(0xFF171827), radius * 0.95f, center)
            drawCircle(Color(0xFF0A0B14), radius * 0.89f, center)

            rotate(rotationDegrees, center) {
                repeat(78) { index ->
                    val ring = if (index % 2 == 0) radius * 0.73f else radius * 0.77f
                    val angle = (index.toFloat() / 78f) * 360f
                    val radians = angle * PI.toFloat() / 180f
                    val from = Offset(
                        x = center.x + kotlin.math.cos(radians) * (ring - 4f),
                        y = center.y + kotlin.math.sin(radians) * (ring - 4f)
                    )
                    val to = Offset(
                        x = center.x + kotlin.math.cos(radians) * (ring + 4f),
                        y = center.y + kotlin.math.sin(radians) * (ring + 4f)
                    )
                    drawLine(Color(0xFF34364B), from, to, strokeWidth = 1.2f)
                }
                drawCircle(Color(0xFF5C4A75), radius * 0.44f, center)
                drawCircle(Color(0xFF25243B), radius * 0.405f, center)
                drawCircle(Color(0xFFF6DD4A), radius * 0.30f, center)
                drawCircle(Color(0xFF242328), radius * 0.07f, center)

                val needle = Path().apply {
                    moveTo(center.x, center.y - radius * 0.28f)
                    lineTo(center.x + radius * 0.055f, center.y - radius * 0.12f)
                    lineTo(center.x - radius * 0.055f, center.y - radius * 0.12f)
                    close()
                }
                drawPath(needle, Color(0xFF171717))
            }
            drawCircle(Color(0xFFDDDCDF), radius * 0.025f, center)
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .background(
                    if (scratching) MaterialTheme.colorScheme.primary else Color(0xFF2A2D45),
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Text(
                text = if (scratching) "SCRATCH" else if (isPlaying) "GIRANDO" else "ARRASTE",
                color = if (scratching) Color(0xFF171717) else Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 9.sp,
                letterSpacing = 0.8.sp
            )
        }
    }
}

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    reverseJogEnabled: Boolean,
    hasTrack: Boolean,
    onPlayPause: () -> Unit,
    onReverseJog: () -> Unit,
    onJogBack: () -> Unit,
    onJogForward: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onJogBack,
            enabled = hasTrack,
            modifier = Modifier.weight(1f),
            colors = OutlinedButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) { Text("− 5s", fontWeight = FontWeight.Black) }

        Button(
            onClick = onPlayPause,
            enabled = hasTrack,
            modifier = Modifier.weight(1.18f),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color(0xFF191919))
        ) { Text(if (isPlaying) "PAUSAR" else "PLAY", fontWeight = FontWeight.Black) }

        OutlinedButton(
            onClick = onJogForward,
            enabled = hasTrack,
            modifier = Modifier.weight(1f),
            colors = OutlinedButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) { Text("+ 5s", fontWeight = FontWeight.Black) }
    }

    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = onReverseJog,
        enabled = hasTrack,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedButtonDefaults.outlinedButtonColors(
            contentColor = if (reverseJogEnabled) Color(0xFFFFA7A0) else MaterialTheme.colorScheme.secondary
        )
    ) {
        Text(
            if (reverseJogEnabled) "PARAR JOG REVERSO" else "JOG REVERSO",
            fontWeight = FontWeight.Black,
            letterSpacing = 0.7.sp
        )
    }
}

@Composable
private fun ProgressControl(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val fraction = positionMs.toFloat() / safeDuration.toFloat()
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = { onSeek((it * safeDuration).roundToLong()) },
            enabled = durationMs > 0L
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(positionMs), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(formatTime(durationMs), color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SpeedAndPitch(speed: Float, pitch: Float, onSpeed: (Float) -> Unit, onPitch: (Float) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13152A))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SliderLine("VELOCIDADE", speed, "%.2fx".format(speed), onSpeed)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFF282B42))
            Spacer(Modifier.height(12.dp))
            SliderLine("PITCH / TOM", pitch, "%.2fx".format(pitch), onPitch)
        }
    }
}

@Composable
private fun SliderLine(label: String, value: Float, display: String, onChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp)
            Text(display, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
        Slider(value = value, onValueChange = onChange, valueRange = 0.5f..2f)
    }
}

@Composable
private fun LoopControls(enabled: Boolean, startMs: Long, endMs: Long, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13152A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("LOOP DE 8 SEGUNDOS", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 13.sp)
                Text(
                    if (enabled) "De ${formatTime(startMs)} até ${formatTime(endMs)}" else "Repete a partir da posição atual",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp
                )
            }
            FilterChip(
                selected = enabled,
                onClick = onToggle,
                label = { Text(if (enabled) "LIGADO" else "LIGAR", fontWeight = FontWeight.Black) }
            )
        }
    }
}

@Composable
private fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17182A))
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Text("COMO USAR", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Toque em Escolher para abrir uma música. Arraste o disco para fazer scratch e posicionar a faixa. “Jog reverso” leva a música para trás em pequenos passos; a inversão real do áudio ficará para o motor avançado da próxima versão.",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}


private fun angleFor(point: Offset, size: androidx.compose.ui.unit.IntSize): Float {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    return Math.toDegrees(atan2((point.y - centerY).toDouble(), (point.x - centerX).toDouble())).toFloat()
}

private fun normalizedAngle(value: Float): Float {
    var normalized = value
    while (normalized > 180f) normalized -= 360f
    while (normalized < -180f) normalized += 360f
    return normalized
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) / 1_000L).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
