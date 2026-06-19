package com.scanforge.app.ui.scan

import android.content.Context
import com.scanforge.app.R
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Supplies default document titles. Lives behind an interface so wording stays in `strings.xml`
 * (resolved with a [Context]) while [ScanViewModel] remains a plain, Context-free unit under test.
 */
interface ScanTitleProvider {
    fun scanTitle(): String
    fun importTitle(): String
}

class AndroidScanTitleProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : ScanTitleProvider {
    override fun scanTitle(): String = context.getString(R.string.scan_document_title, stamp())
    override fun importTitle(): String = context.getString(R.string.scan_imported_title, stamp())
    private fun stamp(): String = LocalDateTime.now().format(TIMESTAMP)

    private companion object {
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ScanTitleModule {
    @Provides
    fun provideScanTitleProvider(impl: AndroidScanTitleProvider): ScanTitleProvider = impl
}
