package com.scanforge.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.scanforge.app.R
import java.io.File

/**
 * Horizontal strip of pages captured in a batch session. Each tile carries a 1-based index, a
 * delete control, and move-earlier / move-later controls (button-based reorder — accessible and
 * deterministic for tests, vs. drag).
 */
@Composable
fun BatchFilmstrip(
    pages: List<CapturedPage>,
    onDelete: (Long) -> Unit,
    onMove: (localId: Long, toEarlier: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.FILMSTRIP),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(pages, key = { it.localId }) { page ->
            val index = pages.indexOf(page)
            FilmstripTile(
                page = page,
                index = index,
                isFirst = index == 0,
                isLast = index == pages.lastIndex,
                onDelete = { onDelete(page.localId) },
                onMove = { earlier -> onMove(page.localId, earlier) },
            )
        }
    }
}

@Composable
private fun FilmstripTile(
    page: CapturedPage,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onDelete: () -> Unit,
    onMove: (toEarlier: Boolean) -> Unit,
) {
    val number = index + 1
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.width(72.dp).height(96.dp)) {
            AsyncImage(
                model = File(page.thumbnailPath),
                contentDescription = stringResource(R.string.cd_page_thumbnail, number),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 72.dp, height = 96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .testTag(TestTags.pageThumb(page.localId)),
            )
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(50))
                    .testTag(TestTags.deletePage(page.localId)),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_delete_page, number),
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Row {
            IconButton(onClick = { onMove(true) }, enabled = !isFirst, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.ChevronLeft,
                    contentDescription = stringResource(R.string.cd_move_page_left, number),
                    tint = if (isFirst) Color.White.copy(alpha = 0.3f) else Color.White,
                )
            }
            IconButton(onClick = { onMove(false) }, enabled = !isLast, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = stringResource(R.string.cd_move_page_right, number),
                    tint = if (isLast) Color.White.copy(alpha = 0.3f) else Color.White,
                )
            }
        }
    }
}
