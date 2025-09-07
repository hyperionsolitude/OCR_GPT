package com.ocrgpt

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UIHelper(
    private val context: Context,
) {
    companion object {
        private const val ONE_MINUTE_MS = 60000L
        private const val ONE_HOUR_MS = 3600000L
        private const val ONE_DAY_MS = 86400000L
        private const val KB_SIZE = 1024L
        private const val MB_SIZE = 1024L * 1024L
        private const val GB_SIZE = 1024L * 1024L * 1024L
    }

    fun copyToClipboard(
        text: String,
        label: String = "OCR Text",
    ) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Log.e("UIHelper", "Security error copying to clipboard: ${e.message}")
            Toast.makeText(context, "Failed to copy text", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            Log.e("UIHelper", "Illegal state error copying to clipboard: ${e.message}")
            Toast.makeText(context, "Failed to copy text", Toast.LENGTH_SHORT).show()
        }
    }

    fun setWebViewContent(
        webView: WebView,
        content: String,
    ) {
        try {
            val htmlContent = generateHtmlContent(content)
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        } catch (e: IllegalArgumentException) {
            Log.e("UIHelper", "Invalid argument error setting WebView content: ${e.message}")
            webView.loadData("<p>Error loading content</p>", "text/html", "UTF-8")
        } catch (e: IllegalStateException) {
            Log.e("UIHelper", "Illegal state error setting WebView content: ${e.message}")
            webView.loadData("<p>Error loading content</p>", "text/html", "UTF-8")
        }
    }

    private fun generateHtmlContent(content: String): String =
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            ${generateCssStyles()}
        </head>
        <body>
            <div class="container">
                $content
            </div>
            ${generateJavaScript()}
        </body>
        </html>
        """.trimIndent()

    private fun generateCssStyles(): String =
        buildString {
            append("<style>")
            append(generateBaseStyles())
            append(generateCodeBlockStyles())
            append(generateTypographyStyles())
            append("</style>")
        }

    private fun generateBaseStyles(): String =
        """
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            line-height: 1.6;
            margin: 20px;
            background-color: #f5f5f5;
        }
        .container {
            max-width: 800px;
            margin: 0 auto;
            background: white;
            padding: 20px;
            border-radius: 8px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        """.trimIndent()

    private fun generateCodeBlockStyles(): String =
        """
        .code-container {
            position: relative;
            background-color: #f8f9fa;
            border: 1px solid #e9ecef;
            border-radius: 4px;
            padding: 15px 15px 15px 15px;
            margin: 10px 0;
        }
        .code-block {
            background-color: #f8f9fa;
            border: 1px solid #e9ecef;
            border-radius: 4px;
            padding: 15px;
            margin: 10px 0;
            font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
            font-size: 14px;
            overflow-x: auto;
            position: relative;
        }
        .copy-btn {
            position: absolute;
            top: 10px;
            right: 10px;
            background: #007bff;
            color: white;
            border: none;
            padding: 5px 10px;
            border-radius: 3px;
            cursor: pointer;
            font-size: 12px;
            z-index: 10;
        }
        .copy-button {
            position: absolute;
            top: 10px;
            right: 10px;
            background: #007bff;
            color: white;
            border: none;
            padding: 5px 10px;
            border-radius: 3px;
            cursor: pointer;
            font-size: 12px;
            z-index: 10;
        }
        .copy-btn:hover {
            background: #0056b3;
        }
        .copy-button:hover {
            background: #0056b3;
        }
        pre {
            white-space: pre-wrap;
            word-wrap: break-word;
            margin-top: 0;
        }
        """.trimIndent()

    private fun generateTypographyStyles(): String =
        """
        h1, h2, h3, h4, h5, h6 {
            color: #333;
            margin-top: 20px;
            margin-bottom: 10px;
        }
        p {
            margin-bottom: 15px;
        }
        ul, ol {
            margin-bottom: 15px;
            padding-left: 20px;
        }
        blockquote {
            border-left: 4px solid #007bff;
            margin: 15px 0;
            padding-left: 15px;
            color: #666;
        }
        """.trimIndent()

    private fun generateJavaScript(): String =
        """
        <script>
            function __copyTextWithFallback(button, text) {
                if (!text) { return; }
                if (navigator && navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText(text)
                        .then(function() { __copiedFeedback(button); })
                        .catch(function() { __androidCopy(button, text); });
                } else {
                    __androidCopy(button, text);
                }
            }

            function __androidCopy(button, text) {
                try {
                    if (window.Android && typeof window.Android.copyToClipboard === 'function') {
                        window.Android.copyToClipboard(text);
                        __copiedFeedback(button);
                    }
                } catch (e) {
                    // swallow
                }
            }

            function __copiedFeedback(button) {
                if (!button) return;
                const original = button.textContent;
                button.textContent = 'Copied!';
                setTimeout(function() { button.textContent = original || 'Copy'; }, 2000);
            }

            // Support both legacy and new handlers
            function copyCode(button) {
                // New style: find sibling pre text
                var container = button && button.parentElement;
                var pre = container ? container.querySelector('pre') : null;
                var text = pre ? pre.textContent : '';
                if (!text && button && button.dataset && button.dataset.code) {
                    // Old style: use data-code attribute
                    text = button.dataset.code;
                }
                __copyTextWithFallback(button, text);
            }

            function copyCodeToClipboard(button) {
                copyCode(button);
            }

            // Event delegation for dynamically injected buttons
            document.addEventListener('click', function(e) {
                var t = e.target;
                if (!t) return;
                if (t.classList && (t.classList.contains('copy-btn') || t.classList.contains('copy-button'))) {
                    copyCode(t);
                }
            }, false);
        </script>
        """.trimIndent()

    fun showToast(
        message: String,
        duration: Int = Toast.LENGTH_SHORT,
    ) {
        try {
            Toast.makeText(context, message, duration).show()
        } catch (e: IllegalStateException) {
            Log.e("UIHelper", "Illegal state error showing toast: ${e.message}")
        }
    }

    fun formatValue(
        value: Long,
        type: FormatType,
    ): String =
        when (type) {
            FormatType.TIME -> {
                val now = System.currentTimeMillis()
                val diff = now - value
                when {
                    diff < ONE_MINUTE_MS -> "Just now"
                    diff < ONE_HOUR_MS -> "${diff / ONE_MINUTE_MS}m ago"
                    diff < ONE_DAY_MS -> "${diff / ONE_HOUR_MS}h ago"
                    else -> "${diff / ONE_DAY_MS}d ago"
                }
            }
            FormatType.SIZE ->
                when {
                    value < KB_SIZE -> "$value B"
                    value < MB_SIZE -> "${value / KB_SIZE} KB"
                    value < GB_SIZE -> "${value / MB_SIZE} MB"
                    else -> "${value / GB_SIZE} GB"
                }
        }

    enum class FormatType {
        TIME,
        SIZE,
    }
}
