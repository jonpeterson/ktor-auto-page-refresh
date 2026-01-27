package example

import io.github.jonpeterson.ktor.autopagerefresh.AutoPageRefresh
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.routing

fun Application.module() {
    if (developmentMode) {
        install(AutoPageRefresh)
    }

    routing {
        staticResources("/", "static")
    }
}
