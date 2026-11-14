# MiniChat – Running Guide

Hướng dẫn chi tiết cách biên dịch và chạy ứng dụng MiniChat trên máy cục bộ hoặc trong mạng LAN.

---

## 1. Yêu cầu môi trường

| Thành phần | Yêu cầu |
|------------|---------|
| Java | **JDK 17 trở lên** (khuyến nghị JDK 25 vì project Eclipse cấu hình `JavaSE-25`). Cần cả `javac` và `java`. |
| Hệ điều hành | Windows, macOS hoặc Linux |
| Terminal | Command Prompt / PowerShell / Terminal |
| Mạng | Các máy trong cùng mạng LAN nếu muốn chat giữa nhiều máy |
| Cổng | TCP **5000** phải trống và được phép qua firewall |

Dự án **không cần** Maven, Gradle hay thư viện ngoài.

### Kiểm tra Java đã cài

```bash
java -version
javac -version
```

Nếu lệnh báo "command not found" / "không được nhận diện", hãy cài JDK (ví dụ [Eclipse Temurin](https://adoptium.net/)) và thêm thư mục `bin` của JDK vào biến môi trường `PATH`.

---

## 2. Cấu trúc cần biết

```
mini-chat/
├── src/
│   ├── server/Server.java
│   ├── server/ClientHandler.java
│   └── client/Client.java
└── bin/            ← nơi chứa file .class sau khi biên dịch
```

Mọi lệnh dưới đây chạy **từ thư mục gốc `mini-chat/`**. Nếu đang ở thư mục khác, hãy `cd` vào trước:

```bash
cd /đường/dẫn/tới/mini-chat
```

---

## 3. Cách 1 – Chạy bằng dòng lệnh (khuyến nghị)

### Bước 1: Biên dịch

**Linux / macOS:**

```bash
javac -encoding UTF-8 -d bin src/server/*.java src/client/*.java
```

**Windows (Command Prompt):**

```bat
javac -encoding UTF-8 -d bin src\server\*.java src\client\*.java
```

**Windows (PowerShell):**

```powershell
javac -encoding UTF-8 -d bin src/server/Server.java src/server/ClientHandler.java src/client/Client.java
```

Giải thích:

- `-encoding UTF-8`: đọc đúng file nguồn có tiếng Việt (chủ yếu trong comment).
- `-d bin`: xuất file `.class` vào thư mục `bin` (đúng cấu hình Eclipse). Thư mục sẽ được tạo tự động nếu chưa có.
- Kết quả mong đợi: `bin/server/Server.class`, `bin/server/ClientHandler.class`, `bin/client/Client.class`.

Nếu biên dịch thành công, **không có output nào** được in ra.

> Muốn build sạch: xóa nội dung `bin/` rồi biên dịch lại.
> - Linux/macOS: `rm -rf bin && javac -encoding UTF-8 -d bin src/server/*.java src/client/*.java`
> - Windows: `rmdir /s /q bin` rồi chạy lại lệnh `javac`.
> - PowerShell: `Remove-Item -Recurse -Force bin`

### Bước 2: Chạy Server

Mở **cửa sổ terminal thứ nhất**:

```bash
java -cp bin server.Server
```

Kết quả mong đợi:

```
================================
       MINI CHAT SERVER
================================
Server dang chay tai cong 5000
Dang cho Client ket noi...
```

Server chạy liên tục và chờ client. **Không đóng cửa sổ này** khi đang chat. Dừng server bằng `Ctrl + C`.

### Bước 3: Chạy Client

Mở **cửa sổ terminal thứ hai**:

```bash
java -cp bin client.Client
```

Kết quả mong đợi:

```
================================
         MINI CHAT CLIENT
================================
Da ket noi Server: localhost:5000
Nhap username:
```

Nhập username (3–20 ký tự chữ, số hoặc `_`), ví dụ `alice`:

```
Nhap username: alice
OK Chao mung alice! Ban da ket noi thanh cong.

Nhap /help de xem cac lenh.
================================
```

Bây giờ bạn có thể gõ tin nhắn hoặc lệnh.

### Bước 4: Chạy thêm nhiều client

Mở thêm terminal thứ ba, tư… và chạy lại:

```bash
java -cp bin client.Client
```

Mỗi client dùng một username **khác nhau**. Để chat thử:

- Cửa sổ `alice`: gõ `Xin chao moi nguoi` → tất cả client nhận `[PUBLIC] alice: Xin chao moi nguoi`.
- Cửa sổ `bob`: gõ `/w alice Chao alice` → chỉ alice nhận tin riêng.
- Cửa sổ `alice`: gõ `/create team1` → tạo nhóm.
- Cửa sổ `bob`: gõ `/join team1` → tham gia nhóm.
- Cửa sổ `bob`: gõ `/g team1 Hello team` → cả nhóm nhận tin.

---

## 4. Cách 2 – Chạy trong Eclipse

Project đã có sẵn `.project` và `.classpath`, tên project là **MiniChat**.

1. Mở Eclipse (**File → Open Projects from File System…** hoặc **File → Import → Existing Projects into Workspace**).
2. Chọn thư mục `mini-chat`, nhấn **Finish**.
3. Đảm bảo project dùng JDK hợp lệ:
   - Chuột phải project → **Properties → Java Build Path → Libraries**.
   - Nếu báo lỗi JRE, chọn **Add Library → JRE System Library** và trỏ tới JDK đã cài (JDK 17+).
4. Chạy server:
   - Mở `src/server/Server.java`.
   - Chuột phải → **Run As → Java Application**.
5. Chạy client:
   - Mở `src/client/Client.java`.
   - Chuột phải → **Run As → Java Application**.
   - Eclipse sẽ mở một tab **Console** riêng cho mỗi lần chạy; muốn nhiều client, bấm **Run** lại nhiều lần.
6. Nếu cần nhập dữ liệu trong Console của Eclipse, hãy chắc chắn con trỏ đang ở tab Console tương ứng trước khi gõ.

Lưu ý: Eclipse tự biên dịch sang `bin/`, không cần chạy `javac` thủ công.

---

## 5. Kết nối giữa nhiều máy trong mạng LAN

Mặc định server lắng nghe trên **tất cả interface** (`new ServerSocket(5000)`), nên máy khác có thể kết nối nếu biết IP.

### Bước 1: Tìm IP của máy chạy server

- **Windows:** `ipconfig` → xem `IPv4 Address` (ví dụ `192.168.1.10`).
- **macOS:** `ipconfig getifaddr en0` (hoặc `en1`).
- **Linux:** `hostname -I` hoặc `ip addr show`.

### Bước 2: Chạy server trên máy A

```bash
java -cp bin server.Server
```

### Bước 3: Từ máy B, truyền IP server làm tham số

```bash
java -cp bin client.Client 192.168.1.10
```

Thay `192.168.1.10` bằng IP thực của máy A. Cú pháp tổng quát:

```bash
java -cp bin client.Client <địa-chỉ-IP-server>
```

Nếu không truyền tham số, client mặc định kết nối `localhost` (chỉ dùng khi client và server cùng máy).

### Bước 4: Mở firewall (nếu cần)

- **Windows:** lần đầu chạy server, Windows Defender sẽ hỏi → chọn **Allow access** cho mạng Private. Hoặc mở thủ công cổng TCP 5000:
  ```bat
  netsh advfirewall firewall add rule name="MiniChat 5000" dir=in action=allow protocol=TCP localport=5000
  ```
- **Linux (ufw):**
  ```bash
  sudo ufw allow 5000/tcp
  ```
- **macOS:** System Settings → Network → Firewall → Options → cho phép `java`.

Nếu hai máy khác mạng LAN (qua Internet), cần mở cổng trên router hoặc dùng VPN; nên cân nhắc bảo mật vì ứng dụng không mã hóa.

---

## 6. Ví dụ một phiên chat

**Server console:**

```
================================
       MINI CHAT SERVER
================================
Server dang chay tai cong 5000
Dang cho Client ket noi...

Ket noi moi: /127.0.0.1:53120
alice da dang nhap.
Ket noi moi: /127.0.0.1:53122
bob da dang nhap.
```

**Client `alice`:**

```
Nhap username: alice
OK Chao mung alice! Ban da ket noi thanh cong.

Nhap /help de xem cac lenh.
================================
> [SERVER] bob da tham gia chat.
> /create team1
> [SERVER] alice da tao nhom team1.
```

**Client `bob`:**

```
Nhap username: bob
OK Chao mung bob! Ban da ket noi thanh cong.
...
> /join team1
> [GROUP team1] bob da tham gia nhom.
> /g team1 Chuc moi nguoi vui ve
> [GROUP team1] bob: Chuc moi nguoi vui ve
```

---

## 7. Bảng lệnh nhanh

| Lệnh | Ý nghĩa |
|------|---------|
| `/help` | Xem hướng dẫn |
| `/users` | Ai đang online |
| `/groups` | Những nhóm hiện có |
| `/create <tên>` | Tạo nhóm (tự vào nhóm) |
| `/join <tên>` | Vào nhóm |
| `/leave <tên>` | Rời nhóm |
| `/g <tên> <nội dung>` | Nhắn nhóm |
| `/w <user> <nội dung>` | Nhắn riêng |
| `/quit` | Thoát |
| `<nội dung>` | Nhắn chung |

Tên nhóm và username: **3–20 ký tự**, chỉ `A–Z`, `a–z`, `0–9`, `_`.

---

## 8. Xử lý sự cố

| Lỗi / hiện tượng | Nguyên nhân | Cách xử lý |
|------------------|-------------|------------|
| `'javac' is not recognized` / `javac: command not found` | Chưa cài JDK hoặc chưa có trong `PATH` | Cài JDK 17+; kiểm tra `javac -version`; thêm `<JDK>/bin` vào `PATH` |
| `java.lang.ClassNotFoundException: server.Server` | Sai thư mục hoặc thiếu `-cp bin` | Chạy đúng từ thư mục gốc với `java -cp bin server.Server`; kiểm tra `bin/server/Server.class` tồn tại |
| `Address already in use` / `BindException` | Cổng 5000 đang bị chiếm | Tắt chương trình đang dùng cổng 5000, hoặc đổi `PORT` trong `Server.java` **và** `Client.java` rồi biên dịch lại |
| `Khong the ket noi Server` / `Connection refused` | Server chưa chạy, sai IP, hoặc firewall chặn | Khởi động server trước; kiểm tra IP; mở cổng TCP 5000 |
| Kết nối được nhưng treo ở "Nhap username" | Server đã dừng hoặc chưa sẵn sàng | Đảm bảo server đang chạy, thử lại |
| `Dang nhap that bai` | Username sai định dạng hoặc đã có người dùng | Dùng 3–20 ký tự chữ/số/`_` và username chưa ai dùng |
| `Username da ton tai` | Trùng tên với người đang online | Chọn username khác |
| Gõ tiếng Việt bị lỗi hiển thị | Console không hỗ trợ UTF-8 | Chạy với `java -Dfile.encoding=UTF-8 -cp bin ...`; hoặc dùng cửa sổ terminal hỗ trợ UTF-8 |
| Client không nhận tin nhắn | Mất kết nối / server dừng | Khởi động lại server và client |
| Sửa code nhưng chạy vẫn như cũ | Chưa biên dịch lại | Chạy lại bước `javac` (hoặc **Build** trong Eclipse) |

### Kiểm tra cổng 5000 đang bị chiếm bởi ai

- **Linux/macOS:**
  ```bash
  lsof -i :5000
  # hoặc
  ss -ltnp | grep 5000
  ```
- **Windows:**
  ```bat
  netstat -ano | findstr :5000
  ```
  Sau đó dùng PID để tìm tiến trình trong Task Manager (hoặc `taskkill /PID <pid> /F`).

### Dừng chương trình

- Client: gõ `/quit` hoặc `Ctrl + C`.
- Server: `Ctrl + C` tại cửa sổ server.

---

## 9. Checklist chạy nhanh (tóm tắt)

```bash
# Tại thư mục gốc mini-chat/
javac -encoding UTF-8 -d bin src/server/*.java src/client/*.java   # 1. build
java -cp bin server.Server                                          # 2. server
java -cp bin client.Client                                          # 3. client #1
java -cp bin client.Client                                          # 4. client #2 (terminal khác)
java -cp bin client.Client 192.168.1.10                             # 5. client từ máy khác
```

1. Cài JDK 17+.
2. Biên dịch vào `bin/`.
3. Chạy server trước.
4. Chạy một hoặc nhiều client.
5. Nhập username duy nhất và chat.
6. Gõ `/quit` để thoát client, `Ctrl + C` để dừng server.
