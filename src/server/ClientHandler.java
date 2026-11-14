
package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {

    private final Socket socket;

    private volatile String username;
    private volatile PrintWriter out;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    // Gửi dữ liệu về Client
    public void send(String message) {
        PrintWriter writer = out;

        if (writer != null) {
            synchronized (writer) {
                writer.println(message);
            }
        }
    }

    @Override
    public void run() {
        try (
            Socket ignored = socket;
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(
                    socket.getOutputStream(), true)
        ) {
            out = writer;

            // Bước 1: nhận username khi Client đăng nhập
            String requestedName = in.readLine();

            if (requestedName == null
                    || !requestedName.matches("[A-Za-z0-9_]{3,20}")) {
                send("ERROR Username phai dai 3-20 ky tu, "
                        + "chi gom chu, so va dau _.");
                return;
            }

            // Không cho phép hai Client dùng cùng username
            if (Server.clients.putIfAbsent(requestedName, this) != null) {
                send("ERROR Username da ton tai.");
                return;
            }

            username = requestedName;

            send("OK Chao mung " + username
                    + "! Ban da ket noi thanh cong.");

            Server.broadcastExcept(
                    "[SERVER] " + username + " da tham gia chat.",
                    this
            );

            System.out.println(username + " da dang nhap.");

            // Bước 2: nhận và xử lý các lệnh liên tục
            String message;

            while ((message = in.readLine()) != null) {
                if (message.equals("/quit")) {
                    break;
                }

                handleMessage(message);
            }

        } catch (IOException e) {
            if (username != null) {
                System.out.println(
                        username + " mat ket noi: " + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    private void handleMessage(String message) {

        if (message.isBlank()) {
            return;
        }

        // Hiển thị trợ giúp
        if (message.equals("/help")) {
            send("[HELP] Lenh: /users, /groups, /create ten_nhom, "
                    + "/join ten_nhom, /leave ten_nhom, "
                    + "/g ten_nhom noi_dung, /w username noi_dung, /quit");
            return;
        }

        // Xem user đang online
        if (message.equals("/users")) {
            send("[USERS] "
                    + (Server.onlineUsers().isEmpty()
                    ? "Chua co user nao."
                    : Server.onlineUsers()));
            return;
        }

        // Xem danh sách nhóm
        if (message.equals("/groups")) {
            send("[GROUPS] "
                    + (Server.groupNames().isEmpty()
                    ? "Chua co nhom nao."
                    : Server.groupNames()));
            return;
        }

        // Tạo nhóm và tự động tham gia nhóm
        if (message.startsWith("/create ")) {
            String groupName = message.substring(8).trim();

            if (!validGroupName(groupName)) {
                send("[ERROR] Ten nhom khong hop le. "
                        + "Dung 3-20 ky tu chu, so hoac _.");
                return;
            }

            Set<ClientHandler> members =
                    ConcurrentHashMap.newKeySet();

            members.add(this);

            if (Server.groups.putIfAbsent(groupName, members) != null) {
                send("[ERROR] Nhom da ton tai.");
                return;
            }

            Server.broadcast(
                    "[SERVER] " + username
                    + " da tao nhom " + groupName + "."
            );
            return;
        }

        // Tham gia nhóm có sẵn
        if (message.startsWith("/join ")) {
            String groupName = message.substring(6).trim();

            Set<ClientHandler> members =
                    Server.groups.get(groupName);

            if (members == null) {
                send("[ERROR] Nhom khong ton tai.");
                return;
            }

            if (members.add(this)) {
                Server.sendToGroup(groupName,
                        "[GROUP " + groupName + "] "
                        + username + " da tham gia nhom.");
            } else {
                send("[INFO] Ban da o trong nhom " + groupName + ".");
            }
            return;
        }

        // Rời nhóm
        if (message.startsWith("/leave ")) {
            String groupName = message.substring(7).trim();

            Set<ClientHandler> members =
                    Server.groups.get(groupName);

            if (members == null || !members.remove(this)) {
                send("[ERROR] Ban khong la thanh vien cua nhom "
                        + groupName + ".");
                return;
            }

            Server.sendToGroup(groupName,
                    "[GROUP " + groupName + "] "
                    + username + " da roi nhom.");

            removeEmptyGroup(groupName);
            send("[INFO] Ban da roi nhom " + groupName + ".");
            return;
        }

        // Chat nhóm: /g ten_nhom noi_dung
        if (message.startsWith("/g ")) {
            String[] parts = message.split(" ", 3);

            if (parts.length < 3 || parts[2].isBlank()) {
                send("[ERROR] Cu phap: /g ten_nhom noi_dung");
                return;
            }

            String groupName = parts[1];
            String content = parts[2];

            Set<ClientHandler> members =
                    Server.groups.get(groupName);

            if (members == null || !members.contains(this)) {
                send("[ERROR] Ban chua tham gia nhom "
                        + groupName + ".");
                return;
            }

            Server.sendToGroup(groupName,
                    "[GROUP " + groupName + "] "
                    + username + ": " + content);
            return;
        }

        // Chat riêng: /w username noi_dung
        if (message.startsWith("/w ")) {
            String[] parts = message.split(" ", 3);

            if (parts.length < 3 || parts[2].isBlank()) {
                send("[ERROR] Cu phap: /w username noi_dung");
                return;
            }

            String targetName = parts[1];
            String content = parts[2];

            ClientHandler target = Server.clients.get(targetName);

            if (target == null) {
                send("[ERROR] User " + targetName
                        + " khong online.");
                return;
            }

            if (target == this) {
                send("[ERROR] Khong the gui tin nhan cho chinh minh.");
                return;
            }

            target.send("[PRIVATE tu " + username + "] " + content);
            send("[PRIVATE den " + targetName + "] " + content);
            return;
        }

        // Không nhận lệnh không hợp lệ
        if (message.startsWith("/")) {
            send("[ERROR] Lenh khong hop le. Nhap /help de xem.");
            return;
        }

        // Tin nhắn thông thường: chat chung
        Server.broadcast("[PUBLIC] " + username + ": " + message);
    }

    private boolean validGroupName(String name) {
        return name != null
                && name.matches("[A-Za-z0-9_]{3,20}");
    }

    private void removeEmptyGroup(String groupName) {
        Set<ClientHandler> members = Server.groups.get(groupName);

        if (members != null
                && members.isEmpty()
                && Server.groups.remove(groupName, members)) {
            Server.broadcast(
                    "[SERVER] Nhom " + groupName
                    + " da bi xoa vi khong con thanh vien."
            );
        }
    }

    private void disconnect() {
        if (username == null) {
            return;
        }

        // Chỉ xóa nếu username vẫn trỏ đến kết nối này
        Server.clients.remove(username, this);

        for (String groupName : Server.groups.keySet()) {
            Set<ClientHandler> members = Server.groups.get(groupName);

            if (members != null && members.remove(this)) {
                Server.sendToGroup(groupName,
                        "[GROUP " + groupName + "] "
                        + username + " da roi nhom.");
                removeEmptyGroup(groupName);
            }
        }

        Server.broadcast(
                "[SERVER] " + username + " da roi chat."
        );

        System.out.println(username + " da ngat ket noi.");
        username = null;
    }
}