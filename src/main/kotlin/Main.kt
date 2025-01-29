import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket;
import java.net.Socket
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.fileSize

val echoPathRegex = Regex("/echo/\\w+")

val filePathRegex = Regex("/files/\\w+")

val userAgentPathRegex = Regex("/user-agent")

val userAgentHeaderRegex = Regex("User-Agent.*")

val supportedCompressionTypes = arrayListOf<String>("gzip")

fun getFilePath(argsMap: Map<String, String>, path: String): Path {
    val directoryFlag = argsMap["--directory"]
    val fileName = path.substringAfter("/files/")
    val filePathString = "$directoryFlag/$fileName"
    return Path(filePathString)
}

fun getEchoPathString(path: String): String {
    return path.split("/")[2]
}

fun getPostRequestBody(reader: BufferedReader, lines: MutableList<String>): String {
    val contentLength = lines.find { it.startsWith("Content-Length:") }
        ?.split(":")
        ?.get(1)
        ?.trim()
        ?.toInt() ?: 0
    val bodyReceiver = CharArray(contentLength)
    if (contentLength > 0) {
        reader.read(bodyReceiver, 0, contentLength)
    }
    return String(bodyReceiver)
}

fun createHttpResponse(
    statusLine: String,
    headers: Map<String, String>,
    body: String
): String {
    val headersString = headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
    return "$statusLine\r\n$headersString\r\n\r\n$body"
}

fun useCompressionMiddleware(lines: MutableList<String>, headers: MutableMap<String, String>) {
    val desiredCompressionSchemes = lines.find { it.startsWith("Accept-Encoding:") }
        ?.split(":", ",")
        ?.drop(1)

    if (!desiredCompressionSchemes.isNullOrEmpty()) {
        for (desiredComp in desiredCompressionSchemes) {
            if (supportedCompressionTypes.contains(desiredComp.trim())) {
                headers["Content-Encoding"] = desiredComp.trim()
            }
        }
    }
}
suspend fun handleRequest(socket: Socket, argsMap: Map<String, String>) {
        socket.use {
            val reader = BufferedReader(InputStreamReader(it.getInputStream()))
            val requestPath = reader.readLine()
            var userAgent = ""
            val path = requestPath.split(" ")[1]
            val method = requestPath.split(" ")[0]
            val writer = PrintWriter(it.getOutputStream(), true)
            var response = ""
            val lines = mutableListOf<String>()
            while (true) {
                var line: String? = reader.readLine()
                if (line.isNullOrEmpty()) break
                lines.add(line)
            }

            var statusLine = "HTTP/1.1 200 OK"
            val headers = mutableMapOf<String, String>()
            var body = ""

            useCompressionMiddleware(lines, headers)

            if (path == "/") {
                statusLine = "HTTP/1.1 200 OK"
            } else if (echoPathRegex.matches(path)) {
                val echoString = getEchoPathString(path)
                val contentLength = echoString.length
                headers["Content-Type"] = "text/plain"
                headers["Content-Length"] = contentLength.toString()
                body = echoString
            } else if (userAgentPathRegex.matches(path)) {
                for (line in lines) {
                    if (userAgentHeaderRegex.matches(line)) {
                        userAgent = line.split(" ")[1]
                    }
                }
                val userAgentLength = userAgent.length
                headers["Content-Length"] = userAgentLength.toString()
                headers["Content-Type"] = "text/plain"
                body = userAgent
            } else if (filePathRegex.matches(path)) {
                when (method) {
                    "GET" -> {
                        val filePath = getFilePath(argsMap, path)
                        if (filePath.exists()) {
                            val fileByteLength = filePath.fileSize()
                            val fileContent = Files.readString(filePath)
                            headers["Content-Type"] = "application/octet-stream"
                            headers["Content-Length"] = fileByteLength.toString()
                            body = fileContent
                        } else {
                            statusLine = "HTTP/1.1 404 Not Found"
                        }
                    }
                    "POST" -> {
                        val body = getPostRequestBody(reader, lines)
                        val filePath = getFilePath(argsMap, path).toString()
                        val file = File(filePath)
                        if (file.exists()) {
                            statusLine = "HTTP/1.1 409 Conflict"
                        } else {
                            file.createNewFile()
                            file.appendText(body)
                            println("File created: $filePath")
                            statusLine = "HTTP/1.1 201 Created"
                        }
                    }
                }
            } else {
                statusLine = "HTTP/1.1 404 Not Found"
            }
            response = createHttpResponse(statusLine, headers, body)
            writer.println(response)
            writer.flush()
            writer.close()
        }
}

fun main(args: Array<String>) = runBlocking {
    println("Logs from your program will appear here!")
    var serverSocket = ServerSocket(4221)
    while (true) {
        serverSocket.reuseAddress = true
        val socket = serverSocket.accept()
        val argsMap = args.toList().chunked(2).associate { it[0] to it[1] }
        CoroutineScope(Dispatchers.IO).launch {handleRequest(socket, argsMap)}
    }
    println("accepted new connection")
}