import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket;

val echoPathRegex = Regex("/echo/\\w+")

fun getEchoPathString(path: String): String {
    return path.split("/")[2]
}
fun main() {
    println("Logs from your program will appear here!")
    var serverSocket = ServerSocket(4221)
    while (true) {
        serverSocket.reuseAddress = true
        serverSocket.accept().use {
            val reader = BufferedReader(InputStreamReader(it.getInputStream()))
            val message = reader.readLine()
            val path = message.split(" ")[1]
            val writer = PrintWriter(it.getOutputStream(), true)
            var response: String
            if (path == "/") {
                response = "HTTP/1.1 200 OK\r\n\r\n"
            } else if (echoPathRegex.matches(path)) {
                val echoString =  getEchoPathString(path)
                val contentLength = echoString.length
                response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: $contentLength\r\n\r\n$echoString"
            } else {
                response = "HTTP/1.1 404 Not Found\r\n\r\n"
            }
            writer.println(response)
            writer.flush()
            writer.close()
        }
    }
    println("accepted new connection")
}