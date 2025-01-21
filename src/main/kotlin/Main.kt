import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket;
import java.net.Socket
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.fileSize

val echoPathRegex = Regex("/echo/\\w+")

val filePathRegex = Regex("/files/\\w+")

val userAgentPathRegex = Regex("/user-agent")

val userAgentHeaderRegex = Regex("User-Agent.*")

fun getFilePath(argsMap: Map<String, String>, path: String): Path {
    val directoryFlag = argsMap["--directory"]
    val fileName = path.substringAfter("/files/")
    val filePathString = directoryFlag + fileName
    return Path(filePathString)
}

fun getEchoPathString(path: String): String {
    return path.split("/")[2]
}
suspend fun handleRequest(socket: Socket, argsMap: Map<String, String>) {
        socket.use {
            val reader = BufferedReader(InputStreamReader(it.getInputStream()))
            val requestPath = reader.readLine()
            var line: String?
            val lines = mutableListOf<String>()
            var userAgent = ""
            val path = requestPath.split(" ")[1]
            val writer = PrintWriter(it.getOutputStream(), true)
            var response: String

            if (path == "/") {
                response = "HTTP/1.1 200 OK\r\n\r\n"
            } else if (echoPathRegex.matches(path)) {
                val echoString = getEchoPathString(path)
                val contentLength = echoString.length
                response =
                    "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: $contentLength\r\n\r\n$echoString"
            } else if (userAgentPathRegex.matches(path)) {
                while (true) {
                    line = reader.readLine()
                    if (line.isNullOrEmpty()) break
                    if (userAgentHeaderRegex.matches(line)) {
                        userAgent = line.split(" ")[1]
                    }
                    lines.add(line)
                }
                val userAgentLength = userAgent.length
                response =
                    "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: $userAgentLength\r\n\r\n$userAgent"
            } else if (filePathRegex.matches(path)) {
                val filePath = getFilePath(argsMap, path)
                if (filePath.exists()) {
                    val fileByteLength = filePath.fileSize()
                    val fileContent = Files.readString(filePath)
                    response = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: $fileByteLength\r\n\r\n${fileContent}"
                } else {
                    response = "HTTP/1.1 404 Not Found\r\n\r\n"
                }
            }
            else {
                response = "HTTP/1.1 404 Not Found\r\n\r\n"
            }
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