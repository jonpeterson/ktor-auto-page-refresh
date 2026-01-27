package io.github.jonpeterson.ktor.autopagerefresh

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.File
import kotlin.time.Duration.Companion.seconds

class AutoPageRefreshTests : FunSpec({
    val client = HttpClient {
        expectSuccess = true
    }
    val baseHtmlContent = "<html><head></head><body><h1>Hello World</h1><h2>Unicode too! こんにちは</h2>"
    val standardHtmlContent = "$baseHtmlContent</body></html>"

    val largeHtmlFile = File("build/large.html").apply {
        createNewFile()
        bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(baseHtmlContent)
            writer.write("<p>")
            repeat(1024 * 1024 * 10) {
                // 10 MB of a's
                writer.write("a")
            }
            writer.write("</p></body></html>")
        }
    }

    lateinit var server: EmbeddedServer<*, *>
    lateinit var baseUrl: String

    suspend fun startServer(pluginConfig: AutoPageRefreshConfiguration.() -> Unit = {}) {
        server = embeddedServer(CIO, port = 0) {
            install(AutoPageRefresh, pluginConfig)

            routing {
                get("index-respondText.html") {
                    call.respondText(contentType = ContentType.Text.Html) { standardHtmlContent }
                }
                get("index-respondBytes.html") {
                    call.respondBytes(contentType = ContentType.Text.Html) { standardHtmlContent.encodeToByteArray() }
                }
                get("index-respondTextWriter.html") {
                    call.respondTextWriter(contentType = ContentType.Text.Html) { write(standardHtmlContent) }
                }
                get("index-respondOutputStream.html") {
                    call.respondOutputStream(contentType = ContentType.Text.Html) { write(standardHtmlContent.encodeToByteArray()) }
                }
                get("index-respondFile.html") {
                    call.respondFile(largeHtmlFile)
                }
                get("preserve-unicode-chars.html") {
                    call.respondText(contentType = ContentType.Text.Html) { standardHtmlContent.replace("Test Page 1", "こんにちは") }
                }
                get("closing-body-tag/has-whitespace.html") {
                    call.respondText(contentType = ContentType.Text.Html) { "$baseHtmlContent\n</ \tbody \t>\n</html>" }
                }
                get("text-plain-content-type.html") {
                    call.respondText(contentType = ContentType.Text.Plain) { standardHtmlContent }
                }
            }
        }.startSuspend(wait = false)
        baseUrl = "http://localhost:${server.engine.resolvedConnectors().first().port}"
    }

    afterTest {
        server.stop()
    }

    afterSpec {
        client.close()
        largeHtmlFile.delete()
    }

    context("Injection of script tag into HTML response bodies") {

        test("Script tag is injected just before closing body tag") {
            startServer()
            listOf(
                "index-respondText.html",
                "index-respondBytes.html",
                "index-respondTextWriter.html",
                "index-respondOutputStream.html",
                "index-respondFile.html",
            ).forEach { page ->
                withClue(page) {
                    client.get("$baseUrl/$page").bodyAsText() shouldEndWith
                        "<script src=\"/server-reload-checker.js\"></script></body></html>"
                }
            }
        }

        test("Script tag is injected, even when it has whitespaces in the tag") {
            startServer()
            client.get("$baseUrl/closing-body-tag/has-whitespace.html").bodyAsText() shouldBe
                "$baseHtmlContent\n<script src=\"/server-reload-checker.js\"></script></ \tbody \t>\n</html>"
        }

        test("Script tag is injected with non-default path") {
            startServer {
                javascriptPath = "dev/refresh.js"
            }
            client.get("$baseUrl/index-respondText.html").bodyAsText() shouldBe
                "$baseHtmlContent<script src=\"/dev/refresh.js\"></script></body></html>"
        }

        test("Script tag should not be injected into non-text/html responses by default") {
            startServer()
            client.get("$baseUrl/text-plain-content-type.html").bodyAsText() shouldBe
                "$baseHtmlContent</body></html>"
        }

        test("Script tag should be injected into non-text/html responses when plugin configured as such") {
            startServer {
                htmlContentTypes = listOf(
                    ContentType.Text.Html,
                    ContentType.Text.Plain,
                )
            }
            client.get("$baseUrl/text-plain-content-type.html").bodyAsText() shouldBe
                "$baseHtmlContent<script src=\"/server-reload-checker.js\"></script></body></html>"
        }
    }

    context("GET `server-reload-checker.js` endpoint") {

        test("Script with default configuration") {
            startServer()
            client.get("$baseUrl/server-reload-checker.js").bodyAsText().also { body ->
                body shouldContain "fetch(\"/server-reload-check\","
                body shouldContain "headers.get(\"X-Server-Reload-Value\")"
                body shouldContain "setInterval(checkReload, 5000)"
            }
        }

        test("Script with custom configuration") {
            startServer {
                endpointPath = "dev/apr"
                headerName = "X-Something-Else"
                javascriptPath = "dev/refresh.js"
                checkInterval = 2.seconds
            }
            client.get("$baseUrl/dev/refresh.js").bodyAsText().also { body ->
                body shouldContain "fetch(\"/dev/apr\","
                body shouldContain "headers.get(\"X-Something-Else\")"
                body shouldContain "setInterval(checkReload, 2000)"
            }
        }
    }

    context("HEAD `server-reload-check` endpoint") {

        test("Value changes when server is reloaded") {
            startServer()
            suspend fun getValue(): String = client.head("$baseUrl/server-reload-check").headers["X-Server-Reload-Value"]!!
            val initialValue = getValue()
            getValue() shouldBe initialValue
            server.reload()
            getValue() shouldNotBe initialValue
        }

        test("Value changes when server with custom configuration is reloaded") {
            startServer {
                endpointPath = "dev/apr"
                headerName = "X-Something-Else"
            }
            suspend fun getValue(): String = client.head("$baseUrl/dev/apr").headers["X-Something-Else"]!!
            val initialValue = getValue()
            getValue() shouldBe initialValue
            server.reload()
            getValue() shouldNotBe initialValue
        }
    }
})
