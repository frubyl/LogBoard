package com.github.logboard.core.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.core.AppenderBase
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

class TelegramAppender : AppenderBase<ILoggingEvent>() {

    var botToken: String = ""
    var chatId: String = ""

    private val httpClient: HttpClient = HttpClient.newHttpClient()
    private val lastSentAt = AtomicLong(0)

    private val timestampFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())

    companion object {
        private const val MAX_MESSAGE_LENGTH = 4096
        private const val MIN_SEND_INTERVAL_MS = 1000L
    }

    override fun start() {
        if (botToken.isBlank() || chatId.isBlank()) {
            addWarn("TelegramAppender: botToken or chatId is not set, appender will not start")
            return
        }
        super.start()
    }

    override fun append(event: ILoggingEvent) {
        val now = System.currentTimeMillis()
        val last = lastSentAt.get()
        if (now - last < MIN_SEND_INTERVAL_MS) {
            return
        }
        if (!lastSentAt.compareAndSet(last, now)) {
            return
        }

        val message = formatMessage(event)
        sendToTelegram(message)
    }

    private fun formatMessage(event: ILoggingEvent): String {
        val sb = StringBuilder()
        val timestamp = timestampFormatter.format(Instant.ofEpochMilli(event.timeStamp))

        sb.append("\u26A0\uFE0F <b>${event.level}</b>\n")
        sb.append("<pre>")
        sb.append("Time:   $timestamp\n")
        sb.append("Logger: ${event.loggerName}\n")
        sb.append("Thread: ${event.threadName}\n\n")
        sb.append(escapeHtml(event.formattedMessage))

        val throwableProxy = event.throwableProxy
        if (throwableProxy != null) {
            sb.append("\n\n")
            sb.append(escapeHtml(renderThrowable(throwableProxy)))
        }

        sb.append("</pre>")

        if (sb.length > MAX_MESSAGE_LENGTH) {
            return sb.substring(0, MAX_MESSAGE_LENGTH - 6) + "...</pre>"
        }
        return sb.toString()
    }

    private fun renderThrowable(proxy: IThrowableProxy): String {
        val sb = StringBuilder()
        sb.append("${proxy.className}: ${proxy.message}\n")
        for (frame in proxy.stackTraceElementProxyArray) {
            sb.append("  at ${frame.steAsString}\n")
        }
        val cause = proxy.cause
        if (cause != null) {
            sb.append("Caused by: ${cause.className}: ${cause.message}\n")
            for (frame in cause.stackTraceElementProxyArray.take(5)) {
                sb.append("  at ${frame.steAsString}\n")
            }
        }
        return sb.toString()
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun sendToTelegram(text: String) {
        try {
            val encodedText = URLEncoder.encode(text, Charsets.UTF_8)
            val url = "https://api.telegram.org/bot$botToken/sendMessage" +
                "?chat_id=$chatId&parse_mode=HTML&text=$encodedText"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build()

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept { response ->
                    if (response.statusCode() !in 200..299) {
                        addWarn("Telegram API returned status ${response.statusCode()}: ${response.body()}")
                    }
                }
                .exceptionally { ex ->
                    addWarn("Failed to send message to Telegram: ${ex.message}")
                    null
                }
        } catch (e: Exception) {
            addWarn("Error preparing Telegram request: ${e.message}")
        }
    }
}
