import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket;
import java.net.Socket
import kotlinx.coroutines.*

val echoPathRegex = Regex("/echo/\\w+")

val userAgentPathRegex = Regex("/user-agent")

val userAgentHeaderRegex = Regex("User-Agent.*")

fun getEchoPathString(path: String): String {
    return path.split("/")[2]
}
suspend fun handleRequest(socket: Socket) {
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
            } else {
                response = "HTTP/1.1 404 Not Found\r\n\r\n"
            }

            writer.println(response)
            writer.flush()
            writer.close()
        }
}

fun main() = runBlocking {
    println("Logs from your program will appear here!")
    var serverSocket = ServerSocket(4221)
    while (true) {
        serverSocket.reuseAddress = true
        val socket = serverSocket.accept()
        CoroutineScope(Dispatchers.IO).launch {handleRequest(socket)}
    }
    println("accepted new connection")
}