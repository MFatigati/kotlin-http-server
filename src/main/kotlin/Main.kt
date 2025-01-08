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
            val writer = PrintWriter(it.getOutputStream(), true)
            val response = "HTTP/1.1 200 OK\r\n\r\n".toByteArray()
            writer.println(response)
            writer.flush()
            writer.close()
        }
    }
     println("accepted new connection")
}
