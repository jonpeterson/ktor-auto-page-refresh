New stuff

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
