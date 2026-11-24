package com.nuvio.app.features.gallery

/** Saves the image at [url] into the user's Downloads folder; the saved file's name, or null on failure. */
internal expect suspend fun saveGalleryImage(url: String, fileName: String): String?
