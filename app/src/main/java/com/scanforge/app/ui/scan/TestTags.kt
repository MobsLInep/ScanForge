package com.scanforge.app.ui.scan

/** Stable testTags for the capture-flow Compose UI tests (state-machine assertions). */
object TestTags {
    const val CAMERA_PREVIEW = "scan_camera_preview"
    const val CAPTURE_BUTTON = "scan_capture_button"
    const val FLASH_TOGGLE = "scan_flash_toggle"
    const val GRID_TOGGLE = "scan_grid_toggle"
    const val BATCH_TOGGLE = "scan_batch_toggle"
    const val IMPORT_BUTTON = "scan_import_button"
    const val FILMSTRIP = "scan_filmstrip"
    const val BATCH_DONE = "scan_batch_done"
    const val CROP_CONFIRM = "scan_crop_confirm"
    const val CROP_RETAKE = "scan_crop_retake"
    const val CROP_HANDLE_PREFIX = "scan_crop_handle_"
    const val PROCESSING = "scan_processing"

    fun pageThumb(localId: Long) = "scan_page_thumb_$localId"
    fun deletePage(localId: Long) = "scan_delete_page_$localId"
}
