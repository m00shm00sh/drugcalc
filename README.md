# drugcalc

This is a drug math calculator written in Kotlin that is spread among multiple modules:

- sharedTypes
  - types used by the decoder, data layer, and calculator
- sharedLogic
  - internal common logic, eg collections, async, and logging
- calc
  - the calculator itself, consisting of business-logic data and calculator
  - caching is handled with [aedile]
- db
  - database access layer; the current implementation uses [Jooq] for SQL queries,
    [Flyway] for SQL migrations, [HikariCP] for SQL connection pooling, and
    [SQLite] as the SQL engine
- server
  - HTTP server providing a REST interface to the calculator; the current implementation uses [Ktor] with plugins:
    - [ktor-routing] for route building
    - [ktor-resources] for typed routing
    - [ktor-content-negotation] for content conversion
    - [ktor-kotlinx-serialization] for using kotlinx.serialization as JSON and resource serializer
    - [ktor-status-pages] for exception rendering
    - [ktor-auth] for handling authorization headers
    - [ktor-auth-jwt] for JWT validation
    - ktor-server-body-limit for limiting JSON input sizes
  - Because Ktor uses the Java 8 key store system, it doesn't support PEM certificates. We use [pem-keystore] to
    work around this in a manner supporting hot-reload of SSL keys
  - config file handling is done with [hoplite] and ktor is used in embedded mode so that we're in charge of reading
    and parsing config

There are other modules used for testing only:
- sharedTestLogic
  - `assertXxx` helpers to extend JUnit and parametric testing wrappers to more richly assert exceptions
- dbTestLogic
  - spins up a testing database for tests needing a DB instance
- calcTestLogic
  - prepares test instance of data controller with various read-only modes
  - populates database with initial values

## Building
Gradle is configured with the following tasks:

| Task                          | Description                                                          |
| -------------------------------|---------------------------------------------------------------------- |
| `./gradlew test`              | Run the tests                                                        |
| `./gradlew build`             | Build everything                                                     |
| `buildFatJar`                 | Build an executable JAR of the server with all dependencies included |
| `buildImage`                  | Build the docker image to use with the fat JAR                       |
| `publishImageToLocalRegistry` | Publish the docker image locally                                     |
| `run`                         | Run the server                                                       |
| `runDocker`                   | Run using the local docker image                                     |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  [...] - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  [...] - Responding at http://0.0.0.0:8080
```

[Jooq]: https://www.jooq.org/
[Flyway]: https://www.red-gate.com/products/flyway/community/
[HikariCP]: https://github.com/brettwooldridge/HikariCP
[SQLite]: https://www.sqlite.org/
[aedile]: https://github.com/sksamuel/aedile
[Ktor]: https://ktor.io/docs/home.html
[ktor-routing]: https://start.ktor.io/p/routing
[ktor-resources]: https://start.ktor.io/p/resources
[ktor-content-negotation]: https://start.ktor.io/p/content-negotiation
[ktor-kotlinx-serialization]: https://start.ktor.io/p/kotlinx-serialization
[ktor-status-pages]: https://start.ktor.io/p/status-pages
[ktor-auth]: https://start.ktor.io/p/auth
[ktor-auth-jwt]: https://start.ktor.io/p/auth-jwt
[pem-keystore]: https://github.com/robymus/simple-pem-keystore
[hoplite]: https://github.com/sksamuel/hoplite

; <!-- | [OpenAPI](https://start.ktor.io/p/openapi)                             | Serves OpenAPI documentation                                                       | !-->
