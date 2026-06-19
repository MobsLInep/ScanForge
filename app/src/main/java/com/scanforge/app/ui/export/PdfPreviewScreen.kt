@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanforge.app.ui.export

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scanforge.app.R
import com.scanforge.designsystem.components.SfTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * In-app PDF preview before sharing, rendered with the platform [PdfRenderer]. Each page is rasterised
 * off the main thread to a bitmap and shown in a vertical scroller. (Password-protected PDFs can't be
 * opened by [PdfRenderer], so the export sheet only offers preview for unencrypted files.)
 */
@Composable
fun PdfPreviewScreen(filePath: String, onBack: () -> Unit) {
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    LaunchedEffect(filePath) {
        pages = withContext(Dispatchers.IO) { renderPdf(File(filePath)) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SfTopBar(title = stringResource(R.string.export_preview_title), onNavigationClick = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(pages) { bitmap ->
                Box(modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White)) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth,
                    )
                }
            }
        }
    }
}

private fun renderPdf(file: File): List<Bitmap> {
    if (!file.exists()) return emptyList()
    return runCatching {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            (0 until renderer.pageCount).map { index ->
                val page = renderer.openPage(index)
                try {
                    val scale = 2
                    val bitmap = Bitmap.createBitmap(
                        page.width * scale,
                        page.height * scale,
                        Bitmap.Config.ARGB_8888,
                    )
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } finally {
                    page.close()
                }
            }
        } finally {
            renderer.close()
            pfd.close()
        }
    }.getOrDefault(emptyList())
}
