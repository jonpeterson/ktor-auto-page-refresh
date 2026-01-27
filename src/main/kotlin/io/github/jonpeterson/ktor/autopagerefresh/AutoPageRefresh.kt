package io.github.jonpeterson.ktor.autopagerefresh

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.routing
import io.ktor.util.date.getTimeMillis
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining
import kotlinx.io.Buffer
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger("io.github.jonpeterson.ktor.autopagerefresh.AutoPageRefresh")

/**
 * A template for a JavaScript script used by the browser to detect when the server was reloaded and should refresh the
 * page.
 *
 * The script performs a `HEAD` request to the specified endpoint path and compares a custom header value with a
 * previously stored value. When the header changes, it forces a full page refresh.
 *
 * The template contains tokens that are replaced by the server at runtime based on configuration parameters.
 * - `%ENDPOINT_PATH%`: the path of the HEAD endpoint to poll.
 * - `%HEADER_NAME%`: the name of the HTTP header that carries the server reload value.
 * - `%INTERVAL_MS%`: how often, in milliseconds, to poll the endpoint.
 */
private const val SERVER_RELOAD_CHECK_JAVASCRIPT_TEMPLATE = """
(function() {
    let prevValue;
    async function checkReload() {
        const response = await fetch("/%ENDPOINT_PATH%", {method: "HEAD", cache: "no-store"});
        const value = response.headers.get("%HEADER_NAME%");
        if (!prevValue) prevValue = value;
        if (value !== prevValue) window.location.reload(true);
    }
    checkReload();
    setInterval(checkReload, %INTERVAL_MS%);
})();
"""

/**
 * A regex matching a closing "body" HTML tag. The pattern allows for whitespace around the tag name.
 */
private val closingBodyTagRegex = Regex("</\\s*body\\s*>")

/**
 * Configuration options for the [AutoPageRefresh] plugin. These properties are set when the plugin is installed on the
 * application.
 */
class AutoPageRefreshConfiguration {

    /**
     * A list of [ContentType]s that should be handled as HTML and where the server reload checker script tags will be
     * injected into.
     */
    var htmlContentTypes = listOf(ContentType.Text.Html)

    /**
     * The path to the JavaScript file that performs client‑side server reload checks. A leading forward-slash is
     * implied. This path is injected as a script tag into HTML pages by this plugin.
     */
    var javascriptPath = "server-reload-checker.js"

    /**
     * The path for the server reload check HEAD endpoint.
     */
    var endpointPath = "server-reload-check"

    /**
     * The name of the HTTP header that carries the server reload value.
     */
    var headerName = "X-Server-Reload-Value"

    /**
     * Defines the duration between checks for a server reload.
     */
    var checkInterval = 5.seconds
}

/**
 * This plugin configures the server to refresh served HTML pages when the server reloads.
 *
 * This is done by...
 * 1. Defining a `HEAD` endpoint that will continue to return the same response header value until the server reloads.
 * 2. Defining a route for a JavaScript file that periodically checks that endpoint for a new response header value, and
 *    if differs from the previous value, refreshes the page.
 * 3. Configuring a response interceptor on outbound HTML responses to inject a reference to the JavaScript file.
 */
val AutoPageRefresh = createApplicationPlugin(
    name = "AutoPageRefresh",
    createConfiguration = {
        AutoPageRefreshConfiguration()
    },
) {
    logger.debug("Configuring AutoPageRefresh plugin")

    application.routing {
        val javascriptContent = SERVER_RELOAD_CHECK_JAVASCRIPT_TEMPLATE
            .replace("%ENDPOINT_PATH%", pluginConfig.endpointPath)
            .replace("%HEADER_NAME%", pluginConfig.headerName)
            .replace("%INTERVAL_MS%", pluginConfig.checkInterval.inWholeMilliseconds.toString())
        val reloadedEpochMs = getTimeMillis()

        logger.debug("Registering `server-reload-check` endpoint at `${pluginConfig.endpointPath}`")
        head(pluginConfig.endpointPath) {
            call.response.header(pluginConfig.headerName, reloadedEpochMs)
            call.response.status(HttpStatusCode.NoContent)
        }

        logger.debug("Registering `server-reload-checker.js` endpoint at `${pluginConfig.javascriptPath}`")
        get(pluginConfig.javascriptPath) {
            call.respondText(javascriptContent, ContentType.Application.JavaScript)
        }
    }

    logger.debug("Registering script tag HTML-injection interceptor")
    onCallRespond { call, message ->

        // Check that message is OutgoingContent
        if (message !is OutgoingContent) {
            return@onCallRespond
        }

        // Check that Content-Type header is set and matches one of the configured types
        val contentType = message.contentType
        if (contentType == null || pluginConfig.htmlContentTypes.none(contentType::match)) {
            return@onCallRespond
        }

        // Transform the body to include the script reference just before the closing body HTML tag
        transformBody { output ->
            val pluginConfig = this@createApplicationPlugin.pluginConfig
            val html = output.responseBodyToString()
            if (html != null) {
                TextContent(
                    text = closingBodyTagRegex.replace(html) { match ->
                        "<script src=\"/${pluginConfig.javascriptPath}\"></script>${match.value}"
                    },
                    contentType = contentType,
                    status = call.response.status(),
                )
            } else {
                logger.debug("Server reloader checker script was not injected due to the response body being '${output::class.qualifiedName}'")
                output
            }
        }
    }
}

/**
 * Converts [this] outgoing response body to a string.
 *
 * Be aware that calling this function on very large and potentially streaming responses will cause the entire body to
 * be accumulated into heap memory.
 */
private suspend fun Any.responseBodyToString(): String? {
    val charset = when (this) {
        is OutgoingContent -> contentType?.charset() ?: Charsets.UTF_8
        else -> Charsets.UTF_8
    }

    return when (this) {
        is OutgoingContent.ByteArrayContent ->
            Buffer().apply { write(bytes()) }.readText(charset)

        is OutgoingContent.ReadChannelContent ->
            readFrom().readRemaining().readText(charset)

        is OutgoingContent.WriteChannelContent ->
            ByteChannel().also { writeTo(it) }.readRemaining().readText(charset)

        is OutgoingContent.ContentWrapper ->
            delegate().responseBodyToString()

        else ->
            null
    }
}
