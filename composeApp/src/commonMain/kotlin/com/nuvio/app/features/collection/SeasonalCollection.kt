package com.nuvio.app.features.collection

import com.nuvio.app.features.home.SeasonalTheme
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

/** Stable identities are independent of the editable display names and folder order. */
object SeasonalCollection {
    const val ID = "nuvio-seasonal"
    const val HOME_KEY = "collection_$ID"
    const val VERSION = 1

    fun theme(folder: CollectionFolder): SeasonalTheme? =
        SeasonalTheme.entries.firstOrNull { it.id == folder.seasonalThemeId }

    fun activeFolder(collection: Collection?, theme: SeasonalTheme): CollectionFolder? =
        collection?.folders?.firstOrNull { it.seasonalThemeId == theme.id }

    fun defaults(): Collection = runBlocking {
        Collection(ID, getString(Res.string.seasonal_collection_title), viewMode = FolderViewMode.ROWS.name,
            folders = SeasonalTheme.entries.map { theme ->
                CollectionFolder(id = "seasonal_${theme.id}", title = getString(theme.title),
                    tileShape = "landscape", seasonalThemeId = theme.id,
                    sources = definitions(theme).map { entry ->
                        CollectionSource(provider = "tmdb", tmdbSourceType = "DISCOVER", title = getString(entry.title),
                            mediaType = entry.mediaType, seasonalCatalog = true, sortBy = "popularity.desc", seasonalKeywords = entry.keywords,
                            filters = TmdbCollectionFilters(withGenres = entry.genres, withoutGenres = entry.excludedGenres,
                                voteCountGte = 100, voteAverageGte = 6.0,
                                releaseDateGte = entry.after, releaseDateLte = entry.before))
                    })
            })
    }

    internal data class Entry(val title: org.jetbrains.compose.resources.StringResource, val genres: String? = null, val keywords: List<String> = emptyList(),
        val before: String? = null, val excludedGenres: String? = null, val after: String? = null, val mediaType: String = "BOTH")

    internal fun definitions(theme: SeasonalTheme): List<Entry> = when (theme) {
        SeasonalTheme.HALLOWEEN -> listOf(
            Entry(Res.string.seasonal_list_spooky_comedy, "27,35"), Entry(Res.string.seasonal_list_family_frights, "10751", listOf("halloween")),
            Entry(Res.string.seasonal_list_slashers, "27", listOf("slasher")), Entry(Res.string.seasonal_list_haunted_houses, null, listOf("haunted house")),
            Entry(Res.string.seasonal_list_supernatural_horror, "27", listOf("supernatural")), Entry(Res.string.seasonal_list_psychological_horror, "27", listOf("psychological horror")),
            Entry(Res.string.seasonal_list_monsters, "27", listOf("monster")), Entry(Res.string.seasonal_list_vampires, null, listOf("vampire")),
            Entry(Res.string.seasonal_list_zombies, null, listOf("zombie")), Entry(Res.string.seasonal_list_witches, null, listOf("witch")),
            Entry(Res.string.seasonal_list_folk_horror, "27", listOf("folk horror")), Entry(Res.string.seasonal_list_found_footage, "27", listOf("found footage")),
            Entry(Res.string.seasonal_list_horror_classics, "27", before = "1999-12-31"))
        SeasonalTheme.CHRISTMAS -> listOf(
            Entry(Res.string.seasonal_list_christmas_with_the_family, "10751", listOf("christmas")), Entry(Res.string.seasonal_list_christmas_comedy, "35", listOf("christmas")),
            Entry(Res.string.seasonal_list_holiday_romance, "10749", listOf("christmas")), Entry(Res.string.seasonal_list_animated_christmas, "16", listOf("christmas")),
            Entry(Res.string.seasonal_list_christmas_classics, keywords = listOf("christmas"), before = "1999-12-31"),
            Entry(Res.string.seasonal_list_modern_christmas_favorites, keywords = listOf("christmas"), after = "2000-01-01"),
            Entry(Res.string.seasonal_list_winter_adventures, "12", listOf("winter")), Entry(Res.string.seasonal_list_christmas_magic, "14", listOf("christmas")),
            Entry(Res.string.seasonal_list_festive_musicals, keywords = listOf("christmas", "musical")), Entry(Res.string.seasonal_list_holiday_mysteries, "9648", listOf("christmas")),
            Entry(Res.string.seasonal_list_dark_christmas, "27", listOf("christmas")), Entry(Res.string.seasonal_list_festive_stories, keywords = listOf("christmas eve"), mediaType = "tv"))
        SeasonalTheme.FRESH_START -> listOf(
            Entry(Res.string.seasonal_list_new_beginnings, keywords = listOf("new beginning")), Entry(Res.string.seasonal_list_finding_yourself, keywords = listOf("self-discovery")),
            Entry(Res.string.seasonal_list_second_chances, keywords = listOf("second chance")), Entry(Res.string.seasonal_list_chasing_dreams, keywords = listOf("dream")),
            Entry(Res.string.seasonal_list_life_changes, "18", listOf("self-discovery")), Entry(Res.string.seasonal_list_starting_over_together, "10749"),
            Entry(Res.string.seasonal_list_against_the_odds, "18", listOf("underdog")), Entry(Res.string.seasonal_list_fresh_perspectives, "99"),
            Entry(Res.string.seasonal_list_feel_good_comedy, "35", listOf("friendship")), Entry(Res.string.seasonal_list_journeys_of_discovery, "12"))
        SeasonalTheme.LOVE -> listOf(
            Entry(Res.string.seasonal_list_romantic_comedy, "10749,35"), Entry(Res.string.seasonal_list_great_love_stories, "10749,18"), Entry(Res.string.seasonal_list_first_love, null, listOf("first love")),
            Entry(Res.string.seasonal_list_love_across_time, "10749", listOf("time travel")), Entry(Res.string.seasonal_list_unexpected_romance, "10749"),
            Entry(Res.string.seasonal_list_love_and_friendship, null, listOf("friendship")), Entry(Res.string.seasonal_list_classic_romance, "10749", before = "1999-12-31"),
            Entry(Res.string.seasonal_list_bittersweet_love, "10749,18", listOf("heartbreak")), Entry(Res.string.seasonal_list_musical_romance, "10749,10402"), Entry(Res.string.seasonal_list_love_and_fantasy, "10749,14"))
        SeasonalTheme.WORLDS -> listOf(
            Entry(Res.string.seasonal_list_space_exploration, "878", listOf("space travel")), Entry(Res.string.seasonal_list_alien_encounters, "878", listOf("alien")),
            Entry(Res.string.seasonal_list_time_travel, null, listOf("time travel")), Entry(Res.string.seasonal_list_distant_futures, "878", listOf("future")),
            Entry(Res.string.seasonal_list_magical_worlds, "14"), Entry(Res.string.seasonal_list_epic_fantasy, "14,12"), Entry(Res.string.seasonal_list_dystopian_worlds, null, listOf("dystopia")),
            Entry(Res.string.seasonal_list_science_fiction_classics, "878", before = "1999-12-31"), Entry(Res.string.seasonal_list_family_fantasy, "14,10751"), Entry(Res.string.seasonal_list_otherworldly_animation, "16", listOf("fantasy world")))
        SeasonalTheme.FAMILY -> listOf(
            Entry(Res.string.seasonal_list_family_favorites, "10751"), Entry(Res.string.seasonal_list_animated_adventures, "16,12"), Entry(Res.string.seasonal_list_animal_friends, "10751", listOf("animal")),
            Entry(Res.string.seasonal_list_family_comedy, "10751,35"), Entry(Res.string.seasonal_list_fairy_tales, null, listOf("fairy tale")), Entry(Res.string.seasonal_list_growing_up, null, listOf("coming of age")),
            Entry(Res.string.seasonal_list_magical_family_stories, "10751,14"), Entry(Res.string.seasonal_list_family_classics, "10751", before = "1999-12-31"),
            Entry(Res.string.seasonal_list_families_together, "10751,18"), Entry(Res.string.seasonal_list_family_adventures, "10751,12"))
        SeasonalTheme.ADVENTURE -> listOf(
            Entry(Res.string.seasonal_list_grand_adventures, "12"), Entry(Res.string.seasonal_list_treasure_hunters, null, listOf("treasure hunt")), Entry(Res.string.seasonal_list_into_the_wild, null, listOf("wilderness")),
            Entry(Res.string.seasonal_list_high_seas, null, listOf("pirate")), Entry(Res.string.seasonal_list_survival_stories, null, listOf("survival")), Entry(Res.string.seasonal_list_epic_quests, "12,14"),
            Entry(Res.string.seasonal_list_action_adventures, "12,28"), Entry(Res.string.seasonal_list_family_expeditions, "12,10751"), Entry(Res.string.seasonal_list_adventure_classics, "12", before = "1999-12-31"), Entry(Res.string.seasonal_list_animated_quests, "12,16"))
        SeasonalTheme.SUMMER -> listOf(
            Entry(Res.string.seasonal_list_summer_escapes, keywords = listOf("summer")), Entry(Res.string.seasonal_list_road_trips, keywords = listOf("road trip")),
            Entry(Res.string.seasonal_list_beach_days, keywords = listOf("beach")), Entry(Res.string.seasonal_list_summer_romance, "10749", listOf("summer")),
            Entry(Res.string.seasonal_list_vacation_comedy, "35", listOf("vacation")), Entry(Res.string.seasonal_list_summer_camp, keywords = listOf("summer camp")),
            Entry(Res.string.seasonal_list_summer_adventures, "12", listOf("summer")), Entry(Res.string.seasonal_list_coming_of_age_summers, keywords = listOf("coming of age", "summer vacation")),
            Entry(Res.string.seasonal_list_island_getaways, keywords = listOf("island")), Entry(Res.string.seasonal_list_summer_thrillers, "53", listOf("summer")))
        SeasonalTheme.SCHOOL -> listOf(
            Entry(Res.string.seasonal_list_back_to_school, keywords = listOf("high school")), Entry(Res.string.seasonal_list_coming_of_age, keywords = listOf("coming of age")),
            Entry(Res.string.seasonal_list_campus_life, keywords = listOf("college")), Entry(Res.string.seasonal_list_teen_comedy, "35", listOf("teenager")),
            Entry(Res.string.seasonal_list_school_romance, "10749", listOf("high school")), Entry(Res.string.seasonal_list_inspiring_teachers, keywords = listOf("teacher")),
            Entry(Res.string.seasonal_list_school_mysteries, "9648", listOf("school")), Entry(Res.string.seasonal_list_friends_forever, keywords = listOf("teenage friendship")),
            Entry(Res.string.seasonal_list_school_sports, keywords = listOf("high school sports")), Entry(Res.string.seasonal_list_school_classics, keywords = listOf("high school"), before = "1999-12-31"))
    }
}
