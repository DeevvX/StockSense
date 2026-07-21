package com.stocksense.app.data.repository

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.roundToInt


object PdfGenerator {

    private const val CSS_PAGE_WIDTH = 794
    private const val CSS_PAGE_HEIGHT = 1123

    private const val PDF_PAGE_WIDTH_PT = 595
    private const val PDF_PAGE_HEIGHT_PT = 842

    private const val READY_RETRIES = 40
    private const val READY_RETRY_DELAY_MS = 100L
    private const val AFTER_PAGE_FINISHED_DELAY_MS = 300L
    private const val AFTER_LAYOUT_DELAY_MS = 250L

    suspend fun generatePdf(
        context: Context,
        html: String,
        fileName: String = "reporte_stocksense_${System.currentTimeMillis()}.pdf"
    ): File {
        val activity = context as? Activity
            ?: throw IllegalArgumentException(
                "PdfGenerator requiere un Context que sea Activity."
            )

        var webView: WebView? = null

        try {
            val prepared = withContext(Dispatchers.Main.immediate) {
                prepareWebView(
                    activity = activity,
                    html = html
                ).also {
                    webView = it.webView
                }
            }

            return withContext(Dispatchers.IO) {
                writePdf(
                    context = activity,
                    prepared = prepared,
                    outputName = sanitizeFileName(fileName)
                )
            }
        } finally {
            withContext(Dispatchers.Main.immediate) {
                webView?.let(::destroyWebView)
            }
        }
    }

    private suspend fun prepareWebView(
        activity: Activity,
        html: String
    ): PreparedWebView {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
            ?: error("No se encontró android.R.id.content.")

        /*
         * density puede ser 1.0, 2.0, 2.75, 3.0, etc.
         * El tamaño físico del WebView debe respetar esa densidad.
         */
        val density = activity.resources.displayMetrics.density

        val physicalPageWidth = (CSS_PAGE_WIDTH * density).roundToInt()
        val physicalPageHeight = (CSS_PAGE_HEIGHT * density).roundToInt()

        val webView = WebView(activity).apply {
            setBackgroundColor(Color.WHITE)
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)

            alpha = 0f
            visibility = View.VISIBLE

            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadsImagesAutomatically = true
                blockNetworkImage = false

                cacheMode = WebSettings.LOAD_NO_CACHE
                defaultTextEncodingName = "UTF-8"

                /*
                 * No permitir que WebView aplique otro autozoom.
                 * La escala se controla exclusivamente con density.
                 */
                useWideViewPort = false
                loadWithOverviewMode = false
                textZoom = 100

                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(false)

                allowFileAccess = false
                allowContentAccess = false

                minimumFontSize = 1
                minimumLogicalFontSize = 1
            }

            /*
             * 100 evita que una escala inicial heredada modifique la relación
             * entre CSS px y density px.
             */
            setInitialScale(100)
        }

        root.addView(
            webView,
            FrameLayout.LayoutParams(
                physicalPageWidth,
                physicalPageHeight
            )
        )

        try {
            awaitPageFinished(webView, html)
            awaitDocumentReady(webView)
            delay(AFTER_PAGE_FINISHED_DELAY_MS)

            val pageCount = readPageCount(webView).coerceAtLeast(1)
            val physicalDocumentHeight = physicalPageHeight * pageCount

            measureAndLayout(
                webView = webView,
                physicalWidth = physicalPageWidth,
                physicalHeight = physicalDocumentHeight
            )

            awaitVisualState(webView)
            delay(AFTER_LAYOUT_DELAY_MS)

            /*
             * Verificación importante:
             * El ancho CSS real debe ser aproximadamente 794.
             */
            val measuredCssWidth = readRootWidth(webView)

            if (measuredCssWidth > CSS_PAGE_WIDTH + 4) {
                throw IllegalStateException(
                    "El HTML está excediendo el ancho A4. " +
                            "Ancho detectado: ${measuredCssWidth}px CSS. " +
                            "Esperado: ${CSS_PAGE_WIDTH}px."
                )
            }

            return PreparedWebView(
                webView = webView,
                pageCount = pageCount,
                density = density,
                physicalPageWidth = physicalPageWidth,
                physicalPageHeight = physicalPageHeight
            )
        } catch (error: Throwable) {
            destroyWebView(webView)
            throw error
        }
    }

    private suspend fun awaitPageFinished(
        webView: WebView,
        html: String
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        var completed = false

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (!completed && continuation.isActive) {
                    completed = true
                    continuation.resume(Unit)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                view: WebView,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                if (!completed && continuation.isActive) {
                    completed = true
                    continuation.resumeWithException(
                        IllegalStateException(
                            "Error WebView $errorCode: ${description.orEmpty()}"
                        )
                    )
                }
            }
        }

        continuation.invokeOnCancellation {
            webView.stopLoading()
        }

        webView.loadDataWithBaseURL(
            "https://stocksense.local/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private suspend fun awaitDocumentReady(webView: WebView) {
        repeat(READY_RETRIES) {
            val ready = evaluateBoolean(
                webView,
                """
                (function() {
                    var fontsReady =
                        !document.fonts ||
                        document.fonts.status === 'loaded';

                    var imagesReady = true;
                    var images = document.images || [];

                    for (var i = 0; i < images.length; i++) {
                        if (!images[i].complete) {
                            imagesReady = false;
                            break;
                        }
                    }

                    return document.readyState === 'complete' &&
                           fontsReady &&
                           imagesReady;
                })();
                """.trimIndent()
            )

            if (ready) return

            delay(READY_RETRY_DELAY_MS)
        }
    }

    private suspend fun readPageCount(
        webView: WebView
    ): Int = evaluateInt(
        webView,
        """
        (function() {
            return document.querySelectorAll('.pdf-page').length;
        })();
        """.trimIndent()
    )

    private suspend fun readRootWidth(
        webView: WebView
    ): Int = evaluateInt(
        webView,
        """
        (function() {
            var root = document.getElementById('pdf-root');
            if (!root) return 0;

            return Math.ceil(
                Math.max(
                    root.scrollWidth,
                    root.offsetWidth,
                    document.documentElement.scrollWidth
                )
            );
        })();
        """.trimIndent()
    )

    private suspend fun evaluateInt(
        webView: WebView,
        script: String
    ): Int = suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript(script) { result ->
            if (continuation.isActive) {
                continuation.resume(
                    result
                        ?.trim('"')
                        ?.toDoubleOrNull()
                        ?.roundToInt()
                        ?: 0
                )
            }
        }
    }

    private suspend fun evaluateBoolean(
        webView: WebView,
        script: String
    ): Boolean = suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript(script) { result ->
            if (continuation.isActive) {
                continuation.resume(result == "true")
            }
        }
    }

    private fun measureAndLayout(
        webView: WebView,
        physicalWidth: Int,
        physicalHeight: Int
    ) {
        webView.layoutParams = webView.layoutParams.apply {
            width = physicalWidth
            height = physicalHeight
        }

        val widthSpec = View.MeasureSpec.makeMeasureSpec(
            physicalWidth,
            View.MeasureSpec.EXACTLY
        )

        val heightSpec = View.MeasureSpec.makeMeasureSpec(
            physicalHeight,
            View.MeasureSpec.EXACTLY
        )

        webView.measure(widthSpec, heightSpec)
        webView.layout(
            0,
            0,
            physicalWidth,
            physicalHeight
        )

        webView.requestLayout()
        webView.invalidate()
    }

    private suspend fun awaitVisualState(webView: WebView) {
        suspendCancellableCoroutine<Unit> { continuation ->
            webView.postVisualStateCallback(
                System.nanoTime(),
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                }
            )
        }
    }

    private fun writePdf(
        context: Context,
        prepared: PreparedWebView,
        outputName: String
    ): File {
        val outputDirectory =
            context.getExternalFilesDir("reports")
                ?: File(context.filesDir, "reports")

        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            error(
                "No se pudo crear el directorio: " +
                        outputDirectory.absolutePath
            )
        }

        val outputFile = File(outputDirectory, outputName)

        /*
         * Evita conservar contenido previo si se reutiliza deliberadamente
         * un mismo nombre.
         */
        if (outputFile.exists()) {
            outputFile.delete()
        }

        val pdfDocument = PdfDocument()
        val bitmapPaint = Paint(
            Paint.ANTI_ALIAS_FLAG or
                    Paint.FILTER_BITMAP_FLAG or
                    Paint.DITHER_FLAG
        )

        try {
            for (pageIndex in 0 until prepared.pageCount) {
                /*
                 * El bitmap final siempre mide 794x1123 píxeles físicos.
                 * El WebView se dibuja reducido por 1/density.
                 */
                val bitmap = Bitmap.createBitmap(
                    CSS_PAGE_WIDTH,
                    CSS_PAGE_HEIGHT,
                    Bitmap.Config.ARGB_8888
                )

                try {
                    val bitmapCanvas = Canvas(bitmap)
                    bitmapCanvas.drawColor(Color.WHITE)

                    val previousAlpha = prepared.webView.alpha
                    prepared.webView.alpha = 1f

                    val saveCount = bitmapCanvas.save()

                    /*
                     * Orden:
                     * 1. Reducir coordenadas físicas a coordenadas CSS.
                     * 2. Desplazar el WebView a la página actual.
                     */
                    bitmapCanvas.scale(
                        1f / prepared.density,
                        1f / prepared.density
                    )

                    bitmapCanvas.translate(
                        0f,
                        -(pageIndex * prepared.physicalPageHeight).toFloat()
                    )

                    prepared.webView.draw(bitmapCanvas)
                    bitmapCanvas.restoreToCount(saveCount)

                    prepared.webView.alpha = previousAlpha

                    val pageInfo = PdfDocument.PageInfo.Builder(
                        PDF_PAGE_WIDTH_PT,
                        PDF_PAGE_HEIGHT_PT,
                        pageIndex + 1
                    ).create()

                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawColor(Color.WHITE)

                    page.canvas.drawBitmap(
                        bitmap,
                        Rect(
                            0,
                            0,
                            CSS_PAGE_WIDTH,
                            CSS_PAGE_HEIGHT
                        ),
                        Rect(
                            0,
                            0,
                            PDF_PAGE_WIDTH_PT,
                            PDF_PAGE_HEIGHT_PT
                        ),
                        bitmapPaint
                    )

                    pdfDocument.finishPage(page)
                } finally {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            }

            FileOutputStream(outputFile, false).use { stream ->
                pdfDocument.writeTo(stream)
                stream.flush()
            }
        } finally {
            pdfDocument.close()
        }

        return outputFile
    }

    private fun destroyWebView(webView: WebView) {
        runCatching {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.removeAllViews()
            webView.destroy()
        }
    }

    private fun sanitizeFileName(fileName: String): String {
        val clean = fileName
            .trim()
            .ifBlank {
                "reporte_stocksense_${System.currentTimeMillis()}.pdf"
            }
            .replace(Regex("""[\\/:*?"<>|]"""), "_")

        return if (clean.endsWith(".pdf", ignoreCase = true)) {
            clean
        } else {
            "$clean.pdf"
        }
    }

    private data class PreparedWebView(
        val webView: WebView,
        val pageCount: Int,
        val density: Float,
        val physicalPageWidth: Int,
        val physicalPageHeight: Int
    )
}