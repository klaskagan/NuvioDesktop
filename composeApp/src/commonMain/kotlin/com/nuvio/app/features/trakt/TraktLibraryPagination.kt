package com.nuvio.app.features.trakt

import com.nuvio.app.features.addons.RawHttpResponse

internal suspend fun <T> fetchTraktLibraryPages(
    fetchPage: suspend (Int) -> RawHttpResponse,
    decodeItems: (String) -> List<T>,
): List<T> {
    val items = mutableListOf<T>()
    var page = 1
    while (true) {
        val response = fetchPage(page)
        val pageItems = decodeItems(response.body)
        items.addAll(pageItems)

        // Trakt can clamp the requested limit. A short page is not the last page.
        val pageCount = response.headers.entries
            .firstOrNull { it.key.equals("x-pagination-page-count", ignoreCase = true) }
            ?.value?.substringBefore(',')?.trim()?.toIntOrNull()
        if (pageItems.isEmpty() || (pageCount != null && page >= pageCount)) break
        // Without pagination headers, continue until an empty page rather than lose items.
        page += 1
    }
    return items
}
