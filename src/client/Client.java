
package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class Client {

    private static final String DEFAULT_HOST = "localhost";
    private static final int PORT = 5000;

    public static void main(String[] args) {

        // Có thể truyền IP Server qua tham số dòng lệnh
        String host = args.length > 0
                ? args[0]
                : DEFAULT_HOST;

        System.out.println("================================");
        System.out.println("         MINI CHAT CLIENT");
        System.out.println("================================");

        try (
            Socket socket = new Socket(host, PORT);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(
                    socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in)
        ) {
            System.out.println("Da ket noi Server: "
                    + host + ":" + PORT);

            System.out.print("Nhap username: ");
            String username = scanner.nextLine().trim();

            // Gửi username đầu tiên để đăng nhập
            out.println(username);

            // Đọc kết quả đăng nhập trước khi khởi động luồng nhận
            String response = in.readLine();

            if (response == null) {
                System.out.println("Server da dong ket noi.");
                return;
            }

            System.out.println(response);

            if (!response.startsWith("OK ")) {
                System.out.println("Dang nhap that bai.");
                return;
            }

            System.out.println();
            System.out.println("Nhap /help de xem cac lenh.");
            System.out.println("================================");

            // Luồng riêng để nhận tin nhắn từ Server
            Thread receiver = new Thread(() -> {
                try {
                    String message;

                    while ((message = in.readLine()) != null) {
                        System.out.println();
                        System.out.println(message);
                        System.out.print("> ");
                    }

                    System.out.println(
                            "\nServer da dong ket noi.");
                } catch (IOException e) {
                    System.out.println(
                            "\nMat ket noi Server.");
                }
            });

            receiver.setDaemon(true);
            receiver.start();

            // Luồng chính: nhập và gửi tin nhắn
            while (scanner.hasNextLine()) {
                System.out.print("> ");
                String message = scanner.nextLine();

                if (message.isBlank()) {
                    continue;
                }

                out.println(message);

                if (message.equals("/quit")) {
                    break;
                }
            }

        } catch (IOException e) {
            System.out.println("Khong the ket noi Server.");
            System.out.println("Chi tiet: " + e.getMessage());
            System.out.println(
                    "Hay kiem tra Server va cong TCP " + PORT + ".");
        }

        System.out.println("Client da ket thuc.");
    }
}