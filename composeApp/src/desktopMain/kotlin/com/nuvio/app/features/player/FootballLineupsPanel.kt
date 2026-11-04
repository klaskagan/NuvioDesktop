package com.nuvio.app.features.player

import com.nuvio.app.core.ui.NuvioAsyncImage
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import org.jetbrains.compose.resources.stringResource
import nuvio.composeapp.generated.resources.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

internal class FootballLineupSession(val initialTitle: String) {
    val selected = mutableStateOf<FootballMatch?>(null)
    val teams = mutableStateOf<List<FootballTeam>>(emptyList())
    val cache = mutableMapOf<String, List<FootballTeam>>()
}

@Composable
internal fun FootballLineupsPanel(modifier: Modifier, close: () -> Unit, session: FootballLineupSession) {
    val searchATeamAndTheOriginalText = stringResource(Res.string.football_lineups_search_a_team_and_the_original)
    val footballDataUnavailableCheckYourKeyText = stringResource(Res.string.football_lineups_football_data_unavailable_check_your_key)
    val lineupsText = stringResource(Res.string.football_lineups_lineups)
    val closeLineupsText = stringResource(Res.string.football_lineups_close_lineups)
    val originalLineupsAreNotAvailableText = stringResource(Res.string.football_lineups_original_lineups_are_not_available)
    val retryText = stringResource(Res.string.football_lineups_retry)
    val originalBenchText = stringResource(Res.string.football_lineups_original_bench)
    var selected by session.selected
    var teams by session.teams
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cache = session.cache
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    fun request(block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch {
            busy = true
            try { block() } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = footballDataUnavailableCheckYourKeyText }
            finally { busy = false }
        }
    }
    LaunchedEffect(session) {
        if (teams.isEmpty()) request {
            PitchApiProvider(FootballKeyStore.read()).use { provider ->
                val found = provider.resolve(session.initialTitle)
                if (found.size == 1) {
                    selected = found.single()
                    teams = cache[found.single().id] ?: provider.lineups(found.single().id)
                    if (teams.isNotEmpty()) cache[found.single().id] = teams
                    message = if (teams.isEmpty()) "Confirmed original lineups are not available yet." else ""
                } else message = "The original match could not be identified uniquely."
            }
        }
    }
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground) {
    Box(Modifier.onPreviewKeyEvent {
        if (it.key == Key.Escape && it.type == KeyEventType.KeyDown) {
            if (selected != null && teams.isEmpty()) { job?.cancel(); busy = false; selected = null } else close()
            true
        } else false
    }) {
    Column(Modifier.fillMaxSize().padding(end = 440.dp).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(lineupsText, style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = close) { Text(closeLineupsText) }
        }
        if (selected == null) {
            Text("Identifying the match automatically…")
            if (!busy) TextButton(onClick = { request {
                PitchApiProvider(FootballKeyStore.read()).use { provider ->
                    val found = provider.resolve(session.initialTitle)
                    if (found.size == 1) {
                        selected = found.single()
                        teams = provider.lineups(found.single().id)
                    } else message = "The original match could not be identified uniquely."
                }
            } }) { Text("Retry") }
        } else {
            Text("${selected!!.home} — ${selected!!.away}", style = MaterialTheme.typography.titleLarge)
            if (!busy && teams.isEmpty()) TextButton(onClick = { request {
                teams = PitchApiProvider(FootballKeyStore.read()).use { it.lineups(selected!!.id) }
                message = if (teams.isEmpty()) originalLineupsAreNotAvailableText else ""
            } }) { Text(retryText) }
            Box(Modifier.fillMaxWidth().heightIn(min = 410.dp).background(Color(0xff153c2d))) {
            Canvas(Modifier.matchParentSize()) {
                val ink = Color.White.copy(alpha = .25f)
                drawRect(ink, style = Stroke(2f))
                drawLine(ink, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2f)
                drawCircle(ink, size.height * .16f, center, style = Stroke(2f))
                drawRect(ink, Offset(0f, size.height * .25f), Size(size.width * .13f, size.height * .5f), style = Stroke(2f))
                drawRect(ink, Offset(size.width * .87f, size.height * .25f), Size(size.width * .13f, size.height * .5f), style = Stroke(2f))
            }
            Row(Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                teams.forEachIndexed { index, side ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        side.badge?.let { NuvioAsyncImage(model = it, contentDescription = null, modifier = Modifier.size(32.dp)) }
                        Text("${side.name} · ${side.formation}", color = Color.White)
                        if (side.starters.isNotEmpty() && side.starters.all { it.pitchX != null && it.pitchY != null }) {
                            BoxWithConstraints(Modifier.fillMaxWidth().height(420.dp)) {
                                side.starters.forEach { player ->
                                    val across = if (index == 0) player.pitchX!! else 1f - player.pitchX!!
                                    val down = if (index == 0) player.pitchY!! else 1f - player.pitchY!!
                                    Column(Modifier.offset(x = (maxWidth - 88.dp) * across, y = 340.dp * down).width(88.dp)) {
                                        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Color.White) {
                                            Text(player.number, Modifier.padding(8.dp), color = Color.Black)
                                        }
                                        Text(player.name.substringAfterLast(" "), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        } else {
                        val rows = side.starters.groupBy { it.grid?.substringBefore(":") ?: "0" }.toSortedMap()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            rows.values.let { if (index == 0) it.toList() else it.toList().reversed() }.forEach { row ->
                                Column(Modifier.weight(1f).heightIn(min = 340.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                                    row.forEach { player ->
                                        Column(Modifier.padding(4.dp)) {
                                            player.photo?.let { NuvioAsyncImage(model = it, contentDescription = null, modifier = Modifier.size(36.dp)) }
                                            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Color.White) {
                                                Text(player.number, Modifier.padding(8.dp), color = Color.Black)
                                            }
                                            Text(player.name.substringAfterLast(" "), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }
            }

        }
        if (selected != null && teams.isEmpty() && message.isNotBlank()) Text(message)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message.isNotBlank() && selected == null) Text(message)
    }
    Column(Modifier.align(Alignment.TopEnd).width(440.dp).fillMaxHeight().padding(top = 280.dp, start = 12.dp, end = 12.dp)
        .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            teams.forEach { side ->
                Text("${side.name} · Coach: ${side.coach}")
                Text(originalBenchText, style = MaterialTheme.typography.titleMedium)
                side.bench.forEach { Text("${it.number}  ${it.name}") }
            }
    }
    }
    }
}
