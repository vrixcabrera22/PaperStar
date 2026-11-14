package com.paperstar.notes.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.res.ResourcesCompat
import com.paperstar.notes.data.MarkType
import com.paperstar.notes.data.Note
import com.paperstar.notes.data.PAGE_W
import com.paperstar.notes.data.PdfLayout
import com.paperstar.notes.data.sizeText
import com.paperstar.notes.ui.note.HighlightColors
import com.paperstar.notes.ui.note.TextColorsLight
import com.paperstar.notes.ui.pen.drawPaper
import com.paperstar.notes.ui.pen.drawStrokes
import com.paperstar.notes.ui.theme.noteFont
import java.io.File
import java.text.DateFormat
import java.util.Date
import android.graphics.Canvas as ACanvas
import androidx.compose.ui.graphics.Canvas as ComposeCanvas

/**
 * Turns a whole note into a PDF: title, typed text with its font and formatting, checklist,
 * tags, list of attached files, then each handwritten page and each picture on its own page.
 * Handwriting is drawn as lines, not a screenshot, so it stays sharp when zoomed or printed.
 */
object NotePdf {
    // A4 in PDF points
    private const val W = 595
    private const val H = 842
    private const val M = 48f
    private const val CONTENT_W = W - 2 * M
    private const val FOOTER = 28f
    private val INK = 0xFF1D1A2C.toInt()
    private val MUTED = 0xFF6C6882.toInt()
    private val ACCENT = 0xFF4F44D6.toInt()
    private val LINE = 0xFFD9D6E6.toInt()

    fun write(ctx: Context, note: Note, jarName: String, out: File) {
        val doc = PdfDocument()
        try {
            Writer(doc).apply {
                textSection(ctx, note, jarName)
                note.pages.forEachIndexed { i, p ->
                    newPage()
                    caption("Handwritten page ${i + 1} of ${note.pages.size}")
                    val avail = bottom - y
                    val s = PdfLayout.fit(PAGE_W, p.height, CONTENT_W, avail)
                    val w = PAGE_W * s; val h = p.height * s
                    val left = M + (CONTENT_W - w) / 2
                    val bg = p.bg?.let { decode(File(ctx.filesDir, it), 1800) }
                    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, ComposeCanvas(canvas), Size(W.toFloat(), H.toFloat())) {
                        translate(left, y) {
                            scale(s, pivot = Offset.Zero) {
                                drawPaper(p.paper, p.height, bg?.asImageBitmap(), p.bg != null)
                                drawStrokes(p.strokes)
                            }
                        }
                    }
                    canvas.drawRect(RectF(left, y, left + w, y + h), Paint().apply { style = Paint.Style.STROKE; color = LINE; strokeWidth = .8f })
                    bg?.recycle()
                }
                note.pictures.forEachIndexed { i, pic ->
                    val bmp = decode(File(ctx.filesDir, pic.path), 2000) ?: return@forEachIndexed
                    newPage()
                    caption("Picture ${i + 1} of ${note.pictures.size}")
                    val s = PdfLayout.fit(bmp.width.toFloat(), bmp.height.toFloat(), CONTENT_W, bottom - y)
                    val w = bmp.width * s; val h = bmp.height * s
                    val left = M + (CONTENT_W - w) / 2
                    canvas.drawBitmap(bmp, null, RectF(left, y, left + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
                    bmp.recycle()
                }
                finish()
            }
            out.parentFile?.mkdirs()
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** Keeps track of the current page and how far down it we've written. */
    private class Writer(val doc: PdfDocument) {
        var page: PdfDocument.Page? = null
        lateinit var canvas: ACanvas
        var y = M
        val bottom get() = H - M - FOOTER
        var number = 0

        fun newPage() {
            finish()
            number++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, number).create())
            canvas = page!!.canvas
            y = M
        }

        fun finish() {
            val p = page ?: return
            val foot = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = MUTED }
            canvas.drawText("Paper Star · $number", M, H - M + 8, foot)
            doc.finishPage(p)
            page = null
        }

        fun ensure(h: Float) { if (page == null || y + h > bottom) newPage() }

        fun caption(text: String) {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = MUTED }
            canvas.drawText(text, M, y + 9, p)
            y += 20
        }

        /** Draws a text layout at [x], moving onto new pages as needed. */
        fun flow(layout: StaticLayout, x: Float = M) {
            val bottoms = (0 until layout.lineCount).map { layout.getLineBottom(it).toFloat() }
            var line = 0
            while (line < layout.lineCount) {
                if (page == null || y + (bottoms[line] - layout.getLineTop(line)) > bottom) newPage()
                val n = PdfLayout.linesThatFit(bottoms, line, bottom - y)
                val top = layout.getLineTop(line).toFloat()
                val end = bottoms[line + n - 1]
                canvas.save()
                canvas.translate(x, y - top)
                canvas.clipRect(0f, top, layout.width.toFloat(), end)
                layout.draw(canvas)
                canvas.restore()
                y += end - top
                line += n
            }
        }

        fun textSection(ctx: Context, note: Note, jarName: String) {
            newPage()
            val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; color = INK; typeface = Typeface.DEFAULT_BOLD }
            flow(layout(note.title.ifBlank { "Untitled" }, title))
            y += 4
            val meta = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = MUTED }
            val date = DateFormat.getDateInstance(DateFormat.LONG).format(Date(note.updatedAt))
            val tags = note.tags.joinToString("  ") { "#$it" }
            flow(layout(listOf(jarName, "Edited $date", tags).filter { it.isNotBlank() }.joinToString("  ·  "), meta))
            y += 14

            if (note.body.isNotBlank()) {
                val f = noteFont(note.font)
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = listOf(10f, 11.5f, 14f)[note.textSize.coerceIn(0, 2)] * f.scale
                    color = INK
                    typeface = f.res?.let { ResourcesCompat.getFont(ctx, it) } ?: Typeface.DEFAULT
                }
                flow(layout(styled(note), tp, spacing = 1.35f))
                y += 14
            }

            if (note.checklist.isNotEmpty()) {
                heading("Checklist")
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = INK }
                val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = MUTED }
                val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f; color = ACCENT; strokeCap = Paint.Cap.ROUND }
                for (item in note.checklist) {
                    val text = SpannableStringBuilder(item.text.ifBlank { " " })
                    if (item.done) text.setSpan(StrikethroughSpan(), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    val l = layout(text, TextPaint(tp).apply { if (item.done) color = MUTED }, width = CONTENT_W - 20)
                    ensure(l.getLineBottom(0).toFloat())
                    val cy = y + l.getLineBaseline(0) - 4
                    canvas.drawRoundRect(RectF(M, cy - 5, M + 10, cy + 5), 2f, 2f, box)
                    if (item.done) {
                        canvas.drawLine(M + 2, cy, M + 4.5f, cy + 3, tick); canvas.drawLine(M + 4.5f, cy + 3, M + 9, cy - 3.5f, tick)
                    }
                    flow(l, M + 20)
                    y += 3
                }
                y += 11
            }

            if (note.files.isNotEmpty()) {
                heading("Attached files")
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = INK }
                for (f in note.files) flow(layout("•  ${f.name}  (${sizeText(f.size)})", tp))
                y += 11
            }
        }

        fun heading(text: String) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; color = INK; typeface = Typeface.DEFAULT_BOLD }
            val l = layout(text, tp)
            ensure(l.height + 30f) // don't leave a heading alone at the bottom of a page
            flow(l)
            y += 6
        }
    }

    private fun layout(text: CharSequence, paint: TextPaint, width: Float = CONTENT_W, spacing: Float = 1.15f): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, spacing).setIncludePad(true).build()

    /** The typed text with its bold, italic, underline, colour and highlight. Printed on white, so light-mode colours. */
    private fun styled(note: Note): CharSequence {
        val s = SpannableStringBuilder(note.body)
        for (m in note.marks) {
            if (m.end > s.length || m.start >= m.end) continue
            val span: Any = when (m.type) {
                MarkType.BOLD -> StyleSpan(Typeface.BOLD)
                MarkType.ITALIC -> StyleSpan(Typeface.ITALIC)
                MarkType.UNDERLINE -> UnderlineSpan()
                MarkType.COLOR -> ForegroundColorSpan(TextColorsLight[m.color.coerceIn(TextColorsLight.indices)].toArgb())
                MarkType.HIGHLIGHT -> BackgroundColorSpan(HighlightColors[m.color.coerceIn(HighlightColors.indices)].toArgb())
            }
            s.setSpan(span, m.start, m.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (m.type == MarkType.HIGHLIGHT) s.setSpan(ForegroundColorSpan(INK), m.start, m.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return s
    }

    private fun decode(f: File, maxPx: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0) return null
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
