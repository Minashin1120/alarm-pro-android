package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentAdapter.LayoutResultCallback
import android.print.PrintDocumentAdapter.WriteResultCallback
import android.print.PrintDocumentInfo
import android.print.pdf.PrintedPdfDocument
import java.io.FileOutputStream

/**
 * Renders the wake-up QR code on a single PDF page for [android.print.PrintManager].
 *
 * Printing matters for this feature: the QR sheet has to be physically placed away from
 * the bed, so "share the image to a printer" is not enough on its own.
 */
class QrPrintDocumentAdapter(
  private val context: Context,
  private val bitmap: Bitmap,
  private val title: String,
) : PrintDocumentAdapter() {

  private var lastAttributes: PrintAttributes? = null

  override fun onLayout(
    oldAttributes: PrintAttributes?,
    newAttributes: PrintAttributes,
    cancellationSignal: CancellationSignal,
    callback: LayoutResultCallback,
    extras: Bundle?,
  ) {
    if (cancellationSignal.isCanceled) {
      callback.onLayoutCancelled()
      return
    }
    lastAttributes = newAttributes
    val info = PrintDocumentInfo.Builder("mathwake_qr.pdf")
      .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
      .setPageCount(1)
      .build()
    callback.onLayoutFinished(info, true)
  }

  override fun onWrite(
    pages: Array<out PageRange>?,
    destination: ParcelFileDescriptor,
    cancellationSignal: CancellationSignal,
    callback: WriteResultCallback,
  ) {
    val attributes = lastAttributes ?: PrintAttributes.Builder().build()
    val document = PrintedPdfDocument(context, attributes)
    try {
      val page = document.startPage(0)
      drawPage(page.canvas, page.info.contentRect)
      document.finishPage(page)

      FileOutputStream(destination.fileDescriptor).use { output ->
        document.writeTo(output)
      }
      callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
    } catch (e: Exception) {
      callback.onWriteFailed(e.message)
    } finally {
      document.close()
    }
  }

  private fun drawPage(canvas: Canvas, contentRect: Rect) {
    canvas.drawColor(Color.WHITE)

    val pageWidth = contentRect.width().toFloat()
    val pageHeight = contentRect.height().toFloat()
    val qrSize = minOf(pageWidth, pageHeight) * 0.6f
    val left = (pageWidth - qrSize) / 2f
    val top = (pageHeight - qrSize) / 2f - qrSize * 0.09f

    val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(bitmap, null, RectF(left, top, left + qrSize, top + qrSize), bitmapPaint)

    val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textAlign = Paint.Align.CENTER
      textSize = pageWidth * 0.05f
      isFakeBoldText = true
    }
    val captionBaseline = top + qrSize + captionPaint.textSize * 1.7f
    canvas.drawText(title, pageWidth / 2f, captionBaseline, captionPaint)

    val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.DKGRAY
      textAlign = Paint.Align.CENTER
      textSize = pageWidth * 0.034f
    }
    canvas.drawText(
      "How to use / ベッドから離れた場所（洗面所など）に貼ってください",
      pageWidth / 2f,
      captionBaseline + notePaint.textSize * 2f,
      notePaint,
    )
    canvas.drawText(
      "アラーム解除時にこのQRコードをスキャンします。他人に見せないでください。",
      pageWidth / 2f,
      captionBaseline + notePaint.textSize * 3.6f,
      notePaint,
    )
  }
}
