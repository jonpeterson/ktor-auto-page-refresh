# ktor-auto-page-refresh
[![CI](https://github.com/jonpeterson/ktor-auto-page-refresh/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/jonpeterson/ktor-auto-page-refresh/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.jonpeterson/ktor-auto-page-refresh.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.github.jonpeterson%22)
[![MIT License](https://img.shields.io/badge/license-MIT-blue)](https://github.com/jonpeterson/ktor-auto-page-refresh/blob/main/LICENSE)

## Background

Ktor server has built-in [auto-reload](https://ktor.io/docs/server-auto-reload.html) functionality when running in development mode.
This allows changes to code and static content to be almost immediately available.
However, when implementing styling changes to web content, continually having to manually refresh the browser can be tedious and there is no solution for this in the official Ktor plugin ecosystem.

## Solution

This library implements a very simple Ktor server plugin that works alongside the server auto-reload mechanism to trigger automatic refreshing of HTML pages in the browser.

The plugin will register an interceptor on served HTML content where it will inject a `<script>` tag referencing a served JavaScript file.
Once loaded, the script will begin polling a `HEAD` endpoint to determine if the server was reloaded since the last poll.
If it's determined that the server was reloaded, the script will trigger a page refresh.

This plugin is intended for development only and should not be installed when running in production.
See the "Installing the plugin" section below for how to only install the plugin for `developmentMode` is set.

## Getting Started

### 1. Add the dependency

The first step is adding this library to your project using a dependency management tool like Gradle or Maven.

#### a. Gradle
```kotlin
dependencies {
    implementation("io.github.jonpeterson:ktor-auto-page-refresh:0.1.0")
}
```

#### b. Maven 
```xml
<dependency>
  <groupId>io.github.jonpeterson</groupId>
  <artifactId>ktor-auto-page-refresh</artifactId>
  <version>0.1.0</version>
</dependency>
```

### 2. Install the plugin

Once the dependency is added to the project, the only remaining step is to install and configure the plugin when the Ktor server starts.
Below are two examples of how Ktor is typically instantiated and configured.

#### a. `embeddedServer` call style

```kotlin
fun main() {
    embeddedServer(Netty, port = 8080) {
        // ...
        if (developmentMode) {
            install(AutoPageRefresh) {
                checkInterval = 3.seconds
            }
        }
        // ...
    }.start(wait = true)
}
```

#### b. `EngineMain` style

```kotlin
fun Application.module() {
    // ...
    if (developmentMode) {
        install(AutoPageRefresh) {
            checkInterval = 3.seconds
        }
    }
    // ...
}
```

See the [Ktor documentation](https://ktor.io/docs/server-plugins.html#install) for more details on installing plugins.

## Configuration

The plugin has a number of configurable parameters that can be set at installation time.
In the code blocks above, note where `checkInterval` is being configured to a non-default value.
Below are the parameters, their descriptions, and default values.

| Property           | Description                                                                                                                                                                               | Default Value                        |
|--------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------|
| `htmlContentTypes` | A list of `ContentType`s for determining which content the reload checker script tags will be injected into.                                                                              | `listOf(ContentType.Text.Html)`      |
| `javascriptPath`   | The path to the JavaScript file that performs client‑side server reload checks. A leading forward-slash is implied. This path is injected as a script tag into HTML pages by this plugin. | `"server-reload-checker.js"`         |
| `endpointPath`     | The path for the server reload check `HEAD` endpoint.                                                                                                                                     | `"server-reload-check"`              |
| `headerName`       | The name of the HTTP header that carries the server reload value.                                                                                                                         | `"X-Server-Reload-Value"`            |
| `checkInterval`    | Duration between checks for a server reload.                                                                                                                                              | `5.seconds` (`kotlin.time.Duration`) |

## Running The Example

The goal of the example is to demonstrate how the plugin works in the simplest form.

In one terminal, run the following Gradle command.
This will run the `example` module as a simple Ktor application serving up static files from the classpath.

```
./gradlew :example:run --args="-config=application-dev.conf"
```

If it's successful, you should see a log line like `Responding at http://0.0.0.0:8080`.
Go ahead and open a browser to that URL and observe the "Hello World" text.

In a second terminal, run the following Gradle command to automatically watch for changes to files in the `example`
module and rebuild as needed.

```
./gradlew -t :example:build
```

Finally, in a third terminal or IDE, make changes to the HTML or Kotlin code the `example` module.
As changes are made and rebuilt, the browser should automatically refresh within a few seconds.

The example application is configured such that this behavior should only be enabled when running in development mode,
so if you restart the application without the `-config` argument above, you should no longer see automatic refreshes of
the page.

Also note that the example application is configured for `DEBUG` level logging for the auto-refresh plugin and in normal
cases, it won't be as noisy.
