package com.nuvio.app.features.tracking

import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * A tracker that keeps a favorites list (Trakt `/sync/favorites`, WeTrakr favorites). Shows and
 * films only; episodes are never favorites.
 */
interface TrackingFavoritesProvider {
    val providerId: TrackingProviderId

    fun supports(media: TrackingMediaReference): Boolean = media.episode == null && media.ids.hasAny

    /** Whether [media] is a favorite, or null when that is not known (yet, or after a failure). */
    suspend fun isFavorite(profileId: Int, media: TrackingMediaReference): Boolean?

    /** Adds or removes [media]; false when the tracker refused. */
    suspend fun setFavorite(profileId: Int, media: TrackingMediaReference, favorite: Boolean): Boolean

    /** Whether this tracker keeps favorite people (WeTrakr does; Trakt's favorites are titles only). */
    val supportsPeople: Boolean get() = false

    /** Whether the person with this TMDB id is a favorite, or null when that is not known. */
    suspend fun isPersonFavorite(profileId: Int, tmdbPersonId: Int): Boolean? = null

    /** Adds or removes a favorite person; false when the tracker refused or keeps no people. */
    suspend fun setPersonFavorite(profileId: Int, tmdbPersonId: Int, favorite: Boolean): Boolean = false
}

data class TrackingFavoriteState(
    val provider: TrackingProviderId? = null,
    val isFavorite: Boolean = false,
) {
    val isAvailable: Boolean get() = provider != null
}

/**
 * The favorite ♥ in the Details Rate strip. It is offered only while a favorites-capable tracker is
 * connected; the first connected one in tracker order answers. A change shows at once and rolls
 * back if the tracker refuses it.
 */
object TrackingFavorites {
    private val providers = MutableStateFlow<Map<TrackingProviderId, TrackingFavoritesProvider>>(emptyMap())
    private val known = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    fun register(provider: TrackingFavoritesProvider) {
        providers.update { it + (provider.providerId to provider) }
    }

    fun observe(media: TrackingMediaReference): Flow<TrackingFavoriteState> =
        combine(providers, TrackingProviderRegistry.connectedProviderIds, known) { _, _, _ -> current(media) }
            .distinctUntilChanged()

    fun current(media: TrackingMediaReference): TrackingFavoriteState {
        val provider = provider(media) ?: return TrackingFavoriteState()
        return TrackingFavoriteState(provider.providerId, known.value[key(provider.providerId, media)] == true)
    }

    /** Reads the favorite once per title and tracker, so the ♥ shows the tracker's answer. */
    suspend fun refresh(media: TrackingMediaReference) {
        val provider = provider(media) ?: return
        val answer = try {
            provider.isFavorite(ProfileRepository.activeProfileId, media)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return
        known.update { it + (key(provider.providerId, media) to answer) }
    }

    suspend fun setFavorite(media: TrackingMediaReference, favorite: Boolean): Boolean = withContext(NonCancellable) {
        val provider = provider(media) ?: return@withContext false
        val key = key(provider.providerId, media)
        val before = known.value[key] == true
        known.update { it + (key to favorite) }
        val saved = try {
            provider.setFavorite(ProfileRepository.activeProfileId, media, favorite)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
        if (!saved) known.update { it + (key to before) }
        saved
    }

    /**
     * The person page's Favorite: offered while a tracker that keeps favorite people is connected
     * (WeTrakr); without one the button is not shown.
     */
    fun observePerson(tmdbPersonId: Int): Flow<TrackingFavoriteState> =
        combine(providers, TrackingProviderRegistry.connectedProviderIds, known) { _, _, _ -> currentPerson(tmdbPersonId) }
            .distinctUntilChanged()

    fun currentPerson(tmdbPersonId: Int): TrackingFavoriteState {
        val provider = personProvider() ?: return TrackingFavoriteState()
        return TrackingFavoriteState(provider.providerId, known.value[personKey(provider.providerId, tmdbPersonId)] == true)
    }

    suspend fun refreshPerson(tmdbPersonId: Int) {
        val provider = personProvider() ?: return
        val answer = try {
            provider.isPersonFavorite(ProfileRepository.activeProfileId, tmdbPersonId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return
        known.update { it + (personKey(provider.providerId, tmdbPersonId) to answer) }
    }

    suspend fun setPersonFavorite(tmdbPersonId: Int, favorite: Boolean): Boolean = withContext(NonCancellable) {
        val provider = personProvider() ?: return@withContext false
        val key = personKey(provider.providerId, tmdbPersonId)
        val before = known.value[key] == true
        known.update { it + (key to favorite) }
        val saved = try {
            provider.setPersonFavorite(ProfileRepository.activeProfileId, tmdbPersonId, favorite)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
        if (!saved) known.update { it + (key to before) }
        saved
    }

    private fun personProvider(): TrackingFavoritesProvider? {
        val connected = TrackingProviderRegistry.connectedProviderIds.value
        return providers.value.values
            .filter { it.providerId in connected && it.supportsPeople }
            .minByOrNull { it.providerId.ordinal }
    }

    private fun personKey(provider: TrackingProviderId, tmdbPersonId: Int) =
        "${ProfileRepository.activeProfileId}:${provider.storageId}:person:$tmdbPersonId"

    private fun provider(media: TrackingMediaReference): TrackingFavoritesProvider? {
        val connected = TrackingProviderRegistry.connectedProviderIds.value
        return providers.value.values
            .filter { it.providerId in connected && it.supports(media) }
            .minByOrNull { it.providerId.ordinal }
    }

    private fun key(provider: TrackingProviderId, media: TrackingMediaReference) =
        "${ProfileRepository.activeProfileId}:${provider.storageId}:${media.stableKey}"
}
