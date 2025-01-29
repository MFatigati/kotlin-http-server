import java.net.ServerSocket;
import java.net.Socket
import kotlinx.coroutines.*
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import java.util.zip.GZIPOutputStream
import java.nio.charset.StandardCharsets.UTF_8

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
    body: String,
    compressedBody: ByteArray?
): Any {
    val headersString = headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
    if (compressedBody?.isNotEmpty() == true) {
        return "$statusLine\r\n$headersString\r\n\r\n".toByteArray(Charsets.US_ASCII) + compressedBody
    }
    return "$statusLine\r\n$headersString\r\n\r\n$body"
}

fun useCompressionHeaderMiddleware(lines: MutableList<String>, headers: MutableMap<String, String>) {
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

fun useCompressionBodyMiddleware(content: String): ByteArray {
    val bos = ByteArrayOutputStream()
    GZIPOutputStream(bos).bufferedWriter(UTF_8).use { it.write(content) }
    return bos.toByteArray()
}

suspend fun handleRequest(socket: Socket, argsMap: Map<String, String>) {
        socket.use {
            val reader = BufferedReader(InputStreamReader(it.getInputStream()))
            val requestPath = reader.readLine()
            var userAgent = ""
            val path = requestPath.split(" ")[1]
            val method = requestPath.split(" ")[0]
            val outputStream = BufferedOutputStream(it.getOutputStream()) // Handle binary responses
            val writer = PrintWriter(it.getOutputStream(), true)
            var response: Any = ""
            val lines = mutableListOf<String>()
            while (true) {
                var line: String? = reader.readLine()
                if (line.isNullOrEmpty()) break
                lines.add(line)
            }

            var statusLine = "HTTP/1.1 200 OK"
            val headers = mutableMapOf<String, String>()
            var body = ""
            var compressedBody: ByteArray? = null

            useCompressionHeaderMiddleware(lines, headers)

            if (path == "/") {
                statusLine = "HTTP/1.1 200 OK"
            } else if (echoPathRegex.matches(path)) {
                val echoString = getEchoPathString(path)
                val contentLength = echoString.length
                headers["Content-Type"] = "text/plain"
                headers["Content-Length"] = contentLength.toString()
                body = echoString
                if (headers["Content-Encoding"].equals("gzip")) {
                    compressedBody = useCompressionBodyMiddleware(echoString)
                    headers["Content-Length"] = compressedBody.size.toString()
                }
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
            response = createHttpResponse(statusLine, headers, body, compressedBody)
            when (response) {
                is ByteArray -> {
                    outputStream.write(response)
                    outputStream.flush()
                }
                is String -> {
                    writer.write(response)
                    writer.flush()
                }
            }
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