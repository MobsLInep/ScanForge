package com.scanforge.core.domain.scanning

import com.scanforge.core.domain.model.NewPage

/**
 * Turns externally picked content into persisted [NewPage]s. URIs are passed as strings so the
 * domain stays Android-free; the data-layer implementation resolves them through `ContentResolver`
 * (images) and `PdfRenderer` (PDFs), saving each into app-private storage via [PageImageStore].
 */
interface PageImporter {
    /** Imports one or more image URIs, in order, as pages. Unreadable items are skipped. */
    suspend fun importImages(uris: List<String>): List<NewPage>

    /** Rasterizes every page of a PDF at [uri] into image pages, in order. */
    suspend fun importPdf(uri: String): List<NewPage>
}
