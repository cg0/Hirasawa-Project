package io.hirasawa.server.webserver.threads

import io.hirasawa.server.Hirasawa
import io.hirasawa.server.plugin.event.web.WebRequestEvent
import io.hirasawa.server.polyfill.readNBytes
import io.hirasawa.server.webserver.Webserver
import io.hirasawa.server.webserver.enums.HttpHeader
import io.hirasawa.server.webserver.enums.HttpMethod
import io.hirasawa.server.webserver.enums.HttpStatus
import io.hirasawa.server.webserver.handlers.CookieHandler
import io.hirasawa.server.webserver.handlers.HttpHeaderHandler
import io.hirasawa.server.webserver.handlers.UrlSegmentHandler
import io.hirasawa.server.webserver.internalroutes.errors.InternalServerErrorRoute
import io.hirasawa.server.webserver.internalroutes.errors.RouteForbidden
import io.hirasawa.server.webserver.objects.Cookie
import io.hirasawa.server.webserver.objects.Request
import io.hirasawa.server.webserver.objects.Response
import java.io.*
import java.net.Socket


class HttpParserThread(private val socket: Socket, private val webserver: Webserver) : Runnable {
    override fun run() {
        val dataInputStream = DataInputStream(socket.getInputStream())

        val headerHandler = HttpHeaderHandler(dataInputStream)
        val immutableHeaders = headerHandler.headers.makeImmutable()
        var postData = ByteArray(0)
        var cookiesReceived = HashMap<String, String>()
        val cookiesToSend = HashMap<String, Cookie>()
        if ("Content-Length" in immutableHeaders &&
            headerHandler.httpMethod == HttpMethod.POST) {
            // Handle POST data
            // We only do this when we're aware of content length
            val postBuffer = ByteArrayOutputStream()
            postBuffer.write(dataInputStream.readNBytes(immutableHeaders["Content-Length"]!!.toInt()))
            postData = postBuffer.toByteArray()
        }

        if ("Cookie" in immutableHeaders) {
            cookiesReceived = CookieHandler(immutableHeaders["Cookie"]!!).cookies
        }

        val urlSegment = UrlSegmentHandler(headerHandler.route).urlSegment

        val host = headerHandler.headers["host"]?.split(":")?.first() ?: ""

        val dataOutputStream = DataOutputStream(socket.getOutputStream())

        val responseBuffer = ByteArrayOutputStream()

        val ipAddress = socket.inetAddress.hostAddress

        val request = Request(urlSegment, headerHandler.httpMethod, immutableHeaders, cookiesReceived,
            ByteArrayInputStream(postData), ipAddress)

        val response = Response(HttpStatus.OK, DataOutputStream(responseBuffer), webserver.getDefaultHeaders(),
            cookiesToSend)

        val webRequestEvent = WebRequestEvent(host, request, response)

        Hirasawa.eventHandler.callEvent(webRequestEvent)

        if (webRequestEvent.isCancelled) {
            RouteForbidden().handle(request, response)
            return
        }

        try {
            webserver.runRoute(host, urlSegment.route, headerHandler.httpMethod,
                request, response)

            if (response.isLoggingEnabled) {
                webserver.accessLogger.log("${request.ipAddress}: ${headerHandler.httpMethod} $host " +
                        urlSegment.route
                )
            }

        } catch (e: Exception) {
            val stringWriter = StringWriter()
            e.printStackTrace(PrintWriter(stringWriter))
            webserver.errorLogger.log(stringWriter)


            // Clean up after errored route
            responseBuffer.reset()

            response.headers.clear()
            for ((key, value) in webserver.getDefaultHeaders()) {
                response.headers[key] = value
            }

            InternalServerErrorRoute().handle(request, response)
        }

        response.headers[HttpHeader.CONTENT_SIZE] = response.outputStream.size()

        // Set version and status
        dataOutputStream.writeBytes("HTTP/1.0 ")
        dataOutputStream.writeBytes(response.httpStatus.code.toString() + " ")
        dataOutputStream.writeBytes(response.httpStatus.toString())
        dataOutputStream.writeBytes("\r\n")

        for ((key, value) in response.headers) {
            dataOutputStream.writeBytes("$key: $value\r\n")
        }

        for ((name, value) in cookiesToSend) {
            dataOutputStream.writeBytes("Set-Cookie: $name=${value.encode()}\r\n")
        }

        dataOutputStream.writeBytes("\r\n")

        dataOutputStream.write(responseBuffer.toByteArray())

        dataOutputStream.close()

    }
}