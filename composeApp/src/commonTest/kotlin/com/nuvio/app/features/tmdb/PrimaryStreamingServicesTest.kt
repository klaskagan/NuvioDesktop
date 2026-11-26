package com.nuvio.app.features.tmdb

import kotlin.test.Test
import kotlin.test.assertEquals

/** Quick look's Watch on listed every store channel and tier TMDB returns for one service. */
class PrimaryStreamingServicesTest {
    @Test fun keepsServicesAndDropsChannelsAndTiers() {
        assertEquals(
            listOf("Paramount Plus"),
            primaryStreamingServices(listOf("Paramount Plus", "Paramount+ Amazon Channel", "Paramount Plus Apple TV Channel", "Paramount+")),
        )
        assertEquals(
            listOf("Netflix", "Amazon Prime Video"),
            primaryStreamingServices(listOf("Netflix", "Netflix Standard with Ads", "Amazon Prime Video", "Amazon Prime Video with Ads")),
        )
    }

    @Test fun keepsChannelsWhenTheyAreAllThereIs() {
        assertEquals(listOf("Starz Amazon Channel"), primaryStreamingServices(listOf("Starz Amazon Channel")))
    }
}
