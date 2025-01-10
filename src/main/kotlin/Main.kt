import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket;
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
            when (path) {
                "/" -> response = "HTTP/1.1 200 OK\r\n\r\n"
                else -> response = "HTTP/1.1 404 Not Found\r\n\r\n"
            }
            writer.println(response)
            writer.flush()
            writer.close()
        }
    }
    println("accepted new connection")
}