package org.http4k.todo

import org.http4k.core.Body
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.OPTIONS
import org.http4k.core.Method.PATCH
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.CorsPolicy.Companion.UnsafeGlobalPermissive
import org.http4k.filter.DebuggingFilters
import org.http4k.filter.ServerFilters.CatchLensFailure
import org.http4k.filter.ServerFilters.Cors
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.lens.string
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer

fun main(args: Array<String>) {
    val port = if (args.isNotEmpty()) args[0] else "5000"
    val baseUrl = if (args.size > 1) args[1] else "http://localhost:$port"
    val todos = TodoDatabase(baseUrl)

    val idLens = Path.string().of("id")
    val todoLens = Body.auto<TodoEntry>().toLens()
    val todoListLens = Body.auto<List<TodoEntry>>().toLens()

    DebuggingFilters
        .PrintRequestAndResponse()
        .then(Cors(UnsafeGlobalPermissive))
        .then(CatchLensFailure)
        .then(routes(
            "/{any:.*}" to OPTIONS bind { _: Request -> Response(OK) },
            "/{id:.+}" to GET bind { request: Request -> todos.find(idLens(request))?.let { Response(OK).with(todoLens of it) } ?: Response(NOT_FOUND) },
            "/" to GET bind { _: Request -> Response(OK).with(todoListLens of todos.all()) },
            "/" to POST bind { request: Request -> Response(OK).with(todoLens of todos.save(null, todoLens(request))) },
            "/{id:.+}" to PATCH bind { request: Request -> Response(OK).with(todoLens of todos.save(idLens(request), todoLens(request))) },
            "/{id:.+}" to DELETE bind { request: Request -> todos.delete(idLens(request))?.let { Response(OK).with(todoLens of it) } ?: Response(NOT_FOUND) },
            "/" to DELETE bind { _: Request -> Response(OK).with(todoListLens of todos.clear()) }
        ))
        .asServer(Jetty(port.toInt())).start().block()
}

data class TodoEntry(val id: String? = null, val url: String? = null, val title: String? = null, val order: Int? = 0, val completed: Boolean? = false)