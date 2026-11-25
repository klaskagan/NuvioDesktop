package com.nuvio.app.features.details.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.i18n.localizedSeasonEpisodeCode
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignPillButton
import com.nuvio.app.core.ui.RedesignPillGroup
import com.nuvio.app.core.ui.RedesignSwitch
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.shuffle.EpisodeShuffleSettings
import com.nuvio.app.features.shuffle.RandomEpisodePicker
import com.nuvio.app.features.shuffle.shuffleEpisodeProgress
import com.nuvio.app.features.shuffle.watchedShuffleEpisodes
import com.nuvio.app.features.streams.rememberPlaybackAvailability
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.cw_action_start_from_beginning
import nuvio.composeapp.generated.resources.detail_board_choose_source
import nuvio.composeapp.generated.resources.detail_board_shuffle_all
import nuvio.composeapp.generated.resources.detail_board_shuffle_keep
import nuvio.composeapp.generated.resources.detail_board_shuffle_keep_line
import nuvio.composeapp.generated.resources.detail_board_shuffle_play
import nuvio.composeapp.generated.resources.detail_board_shuffle_resume
import nuvio.composeapp.generated.resources.detail_board_shuffle_subtitle
import nuvio.composeapp.generated.resources.detail_board_shuffle_unwatched
import nuvio.composeapp.generated.resources.detail_board_shuffle_yours
import nuvio.composeapp.generated.resources.playback_unavailable
import nuvio.composeapp.generated.resources.random_episode_empty_subtitle
import nuvio.composeapp.generated.resources.random_episode_loading
import nuvio.composeapp.generated.resources.random_episode_title
import nuvio.composeapp.generated.resources.shuffle_again
import org.jetbrains.compose.resources.stringResource

/**
 * Shuffle, beside its Your-way action (replacing the bottom sheet on the board): Unwatched or All
 * episodes, the episode it drew with its still and synopsis, Play or Shuffle again, and Keep
 * shuffling, which saves shuffle as on so the next episode is random too. Choose source and Start
 * from the beginning sit in the footer.
 */
@Composable
internal fun BoardShufflePopover(
    meta: MetaDetails,
    settings: EpisodeShuffleSettings,
    onSave: (EpisodeShuffleSettings) -> Boolean,
    watchedKeys: Set<String>,
    progressEntries: List<WatchProgressEntry>,
    blurUnwatchedEpisodes: Boolean,
    onPlay: (MetaVideo) -> Unit,
    onPlayManually: ((MetaVideo) -> Unit)?,
    onStartFromBeginning: (MetaVideo) -> Unit,
    dismiss: () -> Unit,
) {
    val availability = rememberPlaybackAvailability()
    val progress = remember(meta.id, progressEntries) { shuffleEpisodeProgress(meta.id, progressEntries) }
    // Built while composing (a series' episode list is small), so the popover opens at its full
    // size with an episode drawn. Loading it asynchronously opened a short panel that then grew
    // and re-centred beside the action.
    val previousPicker = remember { arrayOfNulls<RandomEpisodePicker>(1) }
    val picker = remember(meta, watchedKeys, progress) {
        RandomEpisodePicker(meta.id, meta.videos, watchedShuffleEpisodes(meta.id, meta.type, meta.videos, watchedKeys), progress)
            .also { it.inheritHistoryFrom(previousPicker[0]); previousPicker[0] = it }
    }
    var includeWatched by remember { mutableStateOf(settings.includeWatched) }
    var keepShuffling by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    val ready: RandomEpisodePicker? = picker
    val unwatchedCount = picker.count(false)
    val allCount = picker.count(true)
    // With nothing left unwatched, the pool is every episode.
    val pool = includeWatched || unwatchedCount == 0
    var drawn by remember { mutableStateOf(picker.pick(pool)) }
    LaunchedEffect(picker, pool) {
        if (drawn?.let { picker.find(it.id, pool) } == null) drawn = picker.pick(pool)
    }
    val episode = drawn?.let { ready?.find(it.id, pool) }

    fun play(action: (MetaVideo) -> Unit) {
        val target = episode ?: return
        if (starting) return
        starting = true
        if (keepShuffling && !onSave(EpisodeShuffleSettings(enabled = true, includeWatched = pool))) {
            starting = false
            return
        }
        dismiss()
        action(target)
    }

    BoardPopoverPanel(
        title = stringResource(Res.string.random_episode_title),
        subtitle = stringResource(Res.string.detail_board_shuffle_subtitle, meta.name),
        onClose = dismiss,
        width = 420.dp,
        tag = "detail_shuffle_popover",
        modifier = Modifier.animateContentSize(tween(220)),
    ) {
        when {
            ready == null -> ShuffleNote(stringResource(Res.string.random_episode_loading))
            allCount == 0 -> ShuffleNote(stringResource(Res.string.random_episode_empty_subtitle))
            else -> {
                RedesignPillGroup(
                    options = listOf(false, true),
                    selected = pool,
                    label = { stringResource(if (it) Res.string.detail_board_shuffle_all else Res.string.detail_board_shuffle_unwatched) },
                    trailing = { (if (it) allCount else unwatchedCount).toString() },
                    onSelect = { all -> if (all || unwatchedCount > 0) includeWatched = all },
                    optionTag = { if (it) "detail_shuffle_all" else "detail_shuffle_unwatched" },
                )
                Spacer(Modifier.height(14.dp))
                AnimatedContent(
                    targetState = episode,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
                    contentKey = { it?.id },
                    label = "shuffle_episode",
                ) { shown ->
                    if (shown != null) ShuffleEpisodeCard(shown, hideStill = blurUnwatchedEpisodes && ready.isWatched(shown).not())
                }
                Spacer(Modifier.height(14.dp))
                val shown = episode
                val code = shown?.let { localizedSeasonEpisodeCode(it.season, it.episode) }.orEmpty()
                val resume = shown != null && progress[shown.season to shown.episode]?.let {
                    !it.isEffectivelyCompleted && (it.lastPositionMs > 0 || it.progressFraction > 0)
                } == true
                val canPlay = shown != null && availability.canPlay(meta.type, shown.id, meta.id, shown.season, shown.episode)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RedesignPillButton(
                        label = when {
                            !canPlay -> stringResource(Res.string.playback_unavailable)
                            resume -> stringResource(Res.string.detail_board_shuffle_resume, code)
                            else -> stringResource(Res.string.detail_board_shuffle_play, code)
                        },
                        onClick = { if (canPlay) play(onPlay) },
                        primary = true,
                        leading = Icons.Rounded.PlayArrow,
                        modifier = Modifier.weight(1f).alpha(if (canPlay && !starting) 1f else 0.6f).testTag("detail_shuffle_play"),
                    )
                    if ((ready.count(pool)) > 1) {
                        RedesignPillButton(
                            label = stringResource(Res.string.shuffle_again),
                            onClick = { if (!starting) drawn = ready.pick(pool) },
                            leading = Icons.Rounded.Shuffle,
                            modifier = Modifier.testTag("detail_shuffle_again"),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                KeepShufflingRow(keepShuffling) { keepShuffling = it }
                val manual = onPlayManually?.takeIf { shown != null && availability.canStream(meta.type, shown.id) }
                if (manual != null || resume) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (manual != null) FooterLink(stringResource(Res.string.detail_board_choose_source), "detail_shuffle_choose") { play(manual) }
                        if (manual != null && resume) Text("·", style = redesignText(12.8.sp, color = RedesignColors.textMuted))
                        if (resume) FooterLink(stringResource(Res.string.cw_action_start_from_beginning), "detail_shuffle_restart") { if (canPlay) play(onStartFromBeginning) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShuffleNote(text: String) {
    Text(text, style = redesignText(13.sp, color = RedesignColors.textSecondary, lineHeight = 18.sp), modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp))
}

@Composable
private fun ShuffleEpisodeCard(episode: MetaVideo, hideStill: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color.White.copy(alpha = 0.06f)).padding(10.dp).testTag("detail_shuffle_episode"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(132.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(RedesignColors.fill), contentAlignment = Alignment.Center) {
            if (!hideStill && !episode.thumbnail.isNullOrBlank()) {
                NuvioAsyncImage(model = episode.thumbnail, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(if (hideStill) Icons.Rounded.VisibilityOff else Icons.Rounded.Shuffle, contentDescription = null, tint = RedesignColors.textMuted, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            BoardKicker(stringResource(Res.string.detail_board_shuffle_yours))
            val code = localizedSeasonEpisodeCode(episode.season, episode.episode)
            Text(
                listOfNotNull(code, episode.title.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = redesignText(15.sp, FontWeight.Bold, lineHeight = 19.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            episode.overview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = redesignText(12.5.sp, color = RedesignColors.textSecondary, lineHeight = 17.5.sp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun KeepShufflingRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (hovered) RedesignColors.fillStrong else RedesignColors.fill)
            .border(1.dp, RedesignColors.hairline, shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Switch) { onChange(!checked) }
            .semantics { toggleableState = if (checked) ToggleableState.On else ToggleableState.Off }
            .testTag("detail_shuffle_keep")
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.detail_board_shuffle_keep), style = redesignText(14.sp, FontWeight.SemiBold))
            Text(stringResource(Res.string.detail_board_shuffle_keep_line), style = redesignText(12.5.sp, color = RedesignColors.textMuted, lineHeight = 17.sp))
        }
        RedesignSwitch(checked)
    }
}

@Composable
internal fun FooterLink(label: String, tag: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Text(
        label,
        style = redesignText(12.8.sp, FontWeight.Medium, if (hovered) RedesignColors.textPrimary else RedesignColors.textMuted),
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .testTag(tag).padding(horizontal = 4.dp, vertical = 3.dp),
    )
}
