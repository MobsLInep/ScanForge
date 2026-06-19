package com.scanforge.app.ui.editor

import android.graphics.Bitmap
import com.scanforge.core.domain.imaging.PageProcessing

/**
 * Complete state of the non-destructive page editor. [original] is the untouched archived capture;
 * [preview] is the live, downscaled result of applying [processing] to it. Editing only ever mutates
 * [processing] — the file on disk is rewritten just once, on [PageEditorViewModel.apply].
 */
data class PageEditorUiState(
    val loading: Boolean = true,
    val originalImagePath: String? = null,
    val processing: PageProcessing = PageProcessing.DEFAULT,
    /** Downscaled "after" preview; null while the first render is in flight. */
    val preview: Bitmap? = null,
    val rendering: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    /** When set, the full-screen corner/crop editor is shown over the page. */
    val showCropEditor: Boolean = false,
    val errorRes: Int? = null,
) {
    val canEdit: Boolean get() = originalImagePath != null && !saving
}
