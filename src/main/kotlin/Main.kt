import java.net.ServerSocket;

fun main() {
    println("Logs from your program will appear here!")

     var serverSocket = ServerSocket(4221)

     serverSocket.reuseAddress = true

     serverSocket.accept()
     println("accepted new connection")
}
