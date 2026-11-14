
package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    public static final int PORT = 5000;

    // Danh sách user đang kết nối: username -> ClientHandler
    static final Map<String, ClientHandler> clients =
            new ConcurrentHashMap<>();

    // Danh sách nhóm: tên nhóm -> tập hợp thành viên
    static final Map<String, Set<ClientHandler>> groups =
            new ConcurrentHashMap<>();

    private static final ExecutorService pool =
            Executors.newCachedThreadPool();

    public static void main(String[] args) {
        System.out.println("================================");
        System.out.println("       MINI CHAT SERVER");
        System.out.println("================================");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server dang chay tai cong " + PORT);
            System.out.println("Dang cho Client ket noi...\n");

            while (true) {
                Socket socket = serverSocket.accept();

                System.out.println(
                        "Ket noi moi: " + socket.getRemoteSocketAddress()
                );

                pool.execute(new ClientHandler(socket));
            }

        } catch (IOException e) {
            System.err.println("Loi Server: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }

    // Gửi tin nhắn đến tất cả Client đang kết nối
    static void broadcast(String message) {
        for (ClientHandler client : clients.values()) {
            client.send(message);
        }
    }

    // Gửi thông báo đến tất cả Client, trừ người gửi
    static void broadcastExcept(
            String message, ClientHandler excluded) {

        for (ClientHandler client : clients.values()) {
            if (client != excluded) {
                client.send(message);
            }
        }
    }

    // Danh sách người dùng đang online
    static String onlineUsers() {
        return String.join(", ", clients.keySet().stream()
                .sorted()
                .toList());
    }

    // Danh sách nhóm hiện có
    static String groupNames() {
        return String.join(", ", groups.keySet().stream()
                .sorted()
                .toList());
    }

    // Gửi tin nhắn đến thành viên của một nhóm
    static void sendToGroup(
            String groupName,
            String message) {

        Set<ClientHandler> members = groups.get(groupName);

        if (members != null) {
            for (ClientHandler member : members) {
                member.send(message);
            }
        }
    }
}