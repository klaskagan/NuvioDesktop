package com.nuvio.app.features.go3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.Go3Playback
import com.nuvio.app.core.ui.isFullscreenActionActive
import com.nuvio.app.core.ui.FullscreenActionButton
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.desktopPageHorizontalPaddingForWidth
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.components.DesktopDetailBackdrop
import com.nuvio.app.features.details.components.DetailActionButtons
import com.nuvio.app.navigation.LocalNativeNavigationBarHidden

/** Uses the same backdrop, primary action and circular back control as desktop details. */
@Composable
fun Go3EventScreen(id: String, onBack: () -> Unit) {
    val fullscreen=isFullscreenActionActive()
    val event=Go3Catalog.find(id)
    var error by remember(id) {mutableStateOf<String?>(null)}
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.background)) {
        val inset=desktopPageHorizontalPaddingForWidth(maxWidth.value)
        val meta=remember(event) {MetaDetails(id=id,type="sport",name=event?.title ?: "Go3",background=event?.artwork,poster=event?.artwork)}
        DesktopDetailBackdrop(meta=meta,viewportHeight=maxHeight,modifier=Modifier.testTag("go3-detail-backdrop"),
            heroTrailerSourceUrl=null,heroTrailerSourceAudioUrl=null,heroTrailerReady=false,
            heroTrailerPlayWhenReady=false,heroTrailerMuted=true,
            onHeroTrailerReady={},onHeroTrailerEnded={},onHeroTrailerError={})
        Column(Modifier.align(Alignment.CenterStart).widthIn(max=820.dp).fillMaxWidth()
            .padding(horizontal=inset,vertical=104.dp).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(24.dp)) {
            Text(event?.title ?: "Go3",style=MaterialTheme.typography.displayLarge.copy(
                fontSize=NuvioTokens.Type.displayMd,lineHeight=NuvioTokens.LineHeight.displayMd,
                fontWeight=FontWeight.ExtraBold,letterSpacing=NuvioTokens.LetterSpacing.none),
                color=MaterialTheme.colorScheme.onBackground)
            Text(event?.schedule ?: "This listing is no longer loaded. Return to Discover.",
                style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurface)
            DetailActionButtons(playLabel=if(event?.state=="upcoming") "Upcoming on Go3" else "Watch on Go3",
                playEnabled=event!=null && event.state!="upcoming",onPlayClick={
                    Go3Playback.fullscreenRequested=fullscreen
                    if(event!=null && Go3Playback.open?.invoke(event.url)!=true)error="Go3 player could not open. Check Go3 settings."
                })
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }
        FullscreenActionButton(modifier=Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top=32.dp,end=inset),
            buttonSize=48.dp,iconSize=24.dp,containerColor=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.82f),
            contentColor=MaterialTheme.colorScheme.onSurface)
        // This provider route owns its header; do not let native-nav suppression hide it.
        CompositionLocalProvider(LocalNativeNavigationBarHidden provides true) {
            NuvioBackButton(onClick=onBack,modifier=Modifier.statusBarsPadding().padding(start=inset,top=32.dp),
                containerColor=Color.Black.copy(alpha=0.34f),showContainerOnDesktop=true,
                contentColor=MaterialTheme.colorScheme.onBackground,buttonSize=48.dp,iconSize=24.dp)
        }
    }
}
