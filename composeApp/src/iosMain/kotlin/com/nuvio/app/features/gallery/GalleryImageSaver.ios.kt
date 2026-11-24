package com.nuvio.app.features.gallery

/** The gallery viewer is desktop-only. */
internal actual suspend fun saveGalleryImage(url: String, fileName: String): String? = null
