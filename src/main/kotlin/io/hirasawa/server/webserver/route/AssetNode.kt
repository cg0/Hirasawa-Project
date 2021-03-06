package io.hirasawa.server.webserver.route

import io.hirasawa.server.webserver.enums.HttpHeader
import io.hirasawa.server.webserver.enums.HttpMethod
import io.hirasawa.server.webserver.internalroutes.errors.RouteNotFoundRoute
import io.hirasawa.server.webserver.objects.Request
import io.hirasawa.server.webserver.objects.Response
import java.io.File
import java.nio.file.Files




class AssetNode(val assetPath: String): RouteNode {
    override fun handle(method: HttpMethod, path: List<String>, request: Request, response: Response) {
        if (File(assetPath).exists()) {
            val filePath = File(assetPath).toPath()
            val mimeType = Files.probeContentType(filePath)

            response.headers[HttpHeader.CONTENT_TYPE] = mimeType
            response.outputStream.write(Files.readAllBytes(filePath))
        } else {
            RouteNotFoundRoute().handle(request, response)
        }
    }
}