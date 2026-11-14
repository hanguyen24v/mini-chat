# MiniChat – Code Explanation

Tài liệu này giải thích cấu trúc mã nguồn, ý nghĩa các hàm/biến quan trọng, cách dữ liệu được trao đổi và luồng hoạt động của ứng dụng MiniChat.

---

## 1. Kiến trúc tổng quan

MiniChat theo mô hình **Client – Server** qua giao thức **TCP**:

```
┌──────────┐        TCP :5000        ┌──────────────────────────────┐
│ Client A │◄───────────────────────►│           Server             │
└──────────┘                         │                              │
┌──────────┐                         │  clients: username -> handler│
│ Client B │◄───────────────────────►│  groups : groupName -> set   │
└──────────┘                         │  ExecutorService (thread pool)│
┌──────────┐                         │                              │
│ Client C │◄───────────────────────►│  mỗi kết nối = 1 ClientHandler│
└──────────┘                         └──────────────────────────────┘
```

- **Server** không có giao diện, chỉ lắng nghe và điều phối.
- **Client** là chương trình console, gửi lệnh dạng văn bản và in những gì server trả về.
- Server không lưu lịch sử; mọi dữ liệu (user, nhóm) nằm trong bộ nhớ và mất khi server dừng.

### Mô hình luồng (threading)

| Thành phần | Luồng |
|------------|-------|
| `Server.main` | 1 luồng chính, chạy vòng `accept()` vô hạn |
| Mỗi kết nối client | 1 `ClientHandler` chạy trong `ExecutorService` (cached thread pool) |
| `Client.main` | 1 luồng chính đọc bàn phím và gửi dữ liệu |
| `Client` receiver | 1 luồng daemon (`setDaemon(true)`) đọc dữ liệu từ server |

Nhờ thread pool, server phục vụ nhiều client đồng thời; nhờ các collection đồng thời (`ConcurrentHashMap`) và `synchronized`, dữ liệu dùng chung vẫn an toàn.

---

## 2. Cấu trúc dữ liệu dùng chung (`Server.java`)

### 2.1. Hằng số và biến

```java
public static final int PORT = 5000;
```
Cổng TCP server lắng nghe. Client cũng dùng hằng `PORT = 5000` riêng trong `Client.java`.

```java
static final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
```
Bảng ánh xạ **username → handler** của người đang online. Dùng `ConcurrentHashMap` vì nhiều thread (nhiều client) cùng đọc/ghi.

```java
static final Map<String, Set<ClientHandler>> groups = new ConcurrentHashMap<>();
```
Bảng ánh xạ **tên nhóm → tập thành viên**. Mỗi tập là một `Set` tạo bằng `ConcurrentHashMap.newKeySet()` (ở `ClientHandler`) để thêm/xóa thành viên an toàn giữa các thread.

```java
private static final ExecutorService pool = Executors.newCachedThreadPool();
```
Thread pool tự tạo thêm thread khi cần và tái sử dụng thread nhàn rỗi. Mỗi kết nối mới được giao cho pool thay vì tạo thread thủ công.

### 2.2. `main(String[] args)` — điểm khởi động

1. In banner **MINI CHAT SERVER**.
2. Mở `ServerSocket` bằng **try-with-resources** → socket tự đóng khi thoát khối `try`.
3. In cổng đang chạy, thông báo đang chờ client.
4. Vòng lặp vô hạn:
   - `serverSocket.accept()` **chặn** cho tới khi có client kết nối, trả về một `Socket`.
   - In địa chỉ client (`getRemoteSocketAddress()`).
   - `pool.execute(new ClientHandler(socket))` → xử lý kết nối trên một thread của pool.
5. `catch (IOException)` in lỗi server (ví dụ cổng bị chiếm).
6. `finally` gọi `pool.shutdown()` để dừng pool khi server kết thúc.

### 2.3. Các hàm tiện ích

| Hàm | Ý nghĩa | Chi tiết |
|-----|---------|----------|
| `broadcast(String message)` | Gửi tin cho **tất cả** user | Duyệt `clients.values()`, gọi `send()` từng handler |
| `broadcastExcept(String message, ClientHandler excluded)` | Gửi cho mọi người **trừ người gửi** | So sánh tham chiếu `client != excluded` |
| `onlineUsers()` | Trả về chuỗi username online | Lấy `clients.keySet()`, sắp xếp alphabet, nối bằng `", "` |
| `groupNames()` | Trả về chuỗi tên nhóm | Tương tự, sắp xếp `groups.keySet()` |
| `sendToGroup(String groupName, String message)` | Gửi tin cho thành viên nhóm | Lấy `Set` thành viên; nếu `null` thì bỏ qua, ngược lại gửi từng người |

`onlineUsers()` / `groupNames()` trả về chuỗi rỗng `""` khi không có dữ liệu; `ClientHandler` kiểm tra `.isEmpty()` để in thông báo thân thiện.

---

## 3. `ClientHandler.java` – xử lý một client

Mỗi đối tượng đại diện cho **một kết nối TCP** và implements `Runnable`.

### 3.1. Thuộc tính

```java
private final Socket socket;
private volatile String username;
private volatile PrintWriter out;
```

- `socket`: kết nối tới client, chỉ gán một lần.
- `username`: tên đăng nhập; `volatile` để các thread khác (broadcast, disconnect) luôn đọc được giá trị mới nhất.
- `out`: luồng ghi tới client; `volatile` để `send()` thấy được khi `run()` khởi tạo.

### 3.2. `send(String message)` – gửi dữ liệu về client

```java
PrintWriter writer = out;
if (writer != null) {
    synchronized (writer) {
        writer.println(message);
    }
}
```

- Đọc `out` vào biến cục bộ (tránh `out` bị đổi giữa chừng).
- `synchronized (writer)` đảm bảo **nhiều thread không ghi xen kẽ** vào cùng một client (ví dụ vừa có tin nhóm vừa có thông báo hệ thống). Nếu không đồng bộ, các dòng có thể bị trộn.
- `println` tự thêm `\n` → phía client đọc theo dòng bằng `readLine()`.

### 3.3. `run()` – vòng đời kết nối

Mở tài nguyên bằng try-with-resources:

```java
try (Socket ignored = socket;
     BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
     PrintWriter writer = new PrintWriter(socket.getOutputStream(), true))
```

- `PrintWriter(..., true)` bật **auto-flush**, mỗi `println` được đẩy đi ngay.
- `out = writer` để các thread khác dùng được.

**Bước 1 – Đăng nhập:**

1. `in.readLine()` đọc dòng đầu tiên do client gửi (username).
2. Kiểm tra `null` hoặc regex `[A-Za-z0-9_]{3,20}`:
   - Chỉ chữ cái, chữ số, dấu gạch dưới.
   - Độ dài 3–20.
   - Sai → gửi `ERROR ...` và `return` (kết thúc handler, đóng socket).
3. `Server.clients.putIfAbsent(requestedName, this)`:
   - Nếu trả về khác `null` nghĩa là username đã tồn tại → `ERROR Username da ton tai.` và thoát.
   - `putIfAbsent` là thao tác **nguyên tử**, tránh hai client cùng lúc chiếm một tên.
4. Gán `username`, gửi `OK Chao mung ...`.
5. `broadcastExcept(...)` thông báo những người khác rằng user vừa tham gia.
6. Ghi log ra console server.

**Bước 2 – Vòng lặp nhận lệnh:**

```java
while ((message = in.readLine()) != null) {
    if (message.equals("/quit")) break;
    handleMessage(message);
}
```

- `readLine()` trả `null` khi client đóng kết nối → thoát vòng lặp.
- `/quit` thoát vòng lặp nhưng vẫn chạy `finally` để dọn dẹp.

**Kết thúc:**

- `catch (IOException)` ghi log mất kết nối (chỉ khi đã đăng nhập).
- `finally` luôn gọi `disconnect()`.

### 3.4. `handleMessage(String message)` – bộ định tuyến lệnh

Hàm này là trái tim của server: nhận một dòng văn bản và quyết định xử lý.

```
message rỗng?                     -> bỏ qua
"/help"                           -> gửi hướng dẫn
"/users"                          -> danh sách online
"/groups"                         -> danh sách nhóm
"/create <ten>"                   -> tạo nhóm, tự join
"/join <ten>"                     -> tham gia nhóm
"/leave <ten>"                    -> rời nhóm, có thể xóa nhóm rỗng
"/g <ten> <noi_dung>"             -> chat nhóm
"/w <user> <noi_dung>"            -> chat riêng
bắt đầu bằng "/" (còn lại)        -> lệnh không hợp lệ
còn lại                           -> chat chung (public)
```

**Chi tiết từng nhánh:**

- **`/help`**: gửi danh sách cú pháp.
- **`/users`**: lấy `Server.onlineUsers()`, nếu rỗng in "Chua co user nao.".
- **`/groups`**: tương tự với `Server.groupNames()`.
- **`/create <ten>`**:
  - Cắt tên bằng `substring(8).trim()` (độ dài chuỗi `"/create "` = 8).
  - `validGroupName` kiểm tra regex; sai → `[ERROR]`.
  - Tạo `Set` thành viên bằng `ConcurrentHashMap.newKeySet()`, thêm `this`.
  - `putIfAbsent` đảm bảo không trùng nhóm; trùng → `[ERROR] Nhom da ton tai.`
  - Thành công → broadcast thông báo toàn server.
- **`/join <ten>`**:
  - Lấy tập thành viên theo tên; `null` → nhóm không tồn tại.
  - `members.add(this)` trả `true` nếu chưa ở trong nhóm → thông báo cho cả nhóm; `false` → báo "đã ở trong nhóm".
- **`/leave <ten>`**:
  - `members == null || !members.remove(this)` → báo không phải thành viên.
  - Nếu xóa thành công → thông báo nhóm, gọi `removeEmptyGroup()` (xóa nhóm nếu rỗng), báo lại cho người rời.
- **`/g <ten> <noi_dung>`**:
  - `split(" ", 3)` chia tối đa 3 phần: `/g`, tên nhóm, phần nội dung còn lại (giữ được dấu cách trong nội dung).
  - Thiếu nội dung → báo cú pháp.
  - Nhóm không tồn tại **hoặc** người gửi không phải thành viên → `[ERROR]`.
  - Hợp lệ → `sendToGroup` với tiền tố `[GROUP <ten>]`.
- **`/w <user> <noi_dung>`**:
  - Tương tự `split(" ", 3)`.
  - Tìm người nhận trong `Server.clients`; không có → báo offline.
  - `target == this` → không cho tự nhắn.
  - Gửi cho người nhận `[PRIVATE tu <sender>]` và gửi lại cho người gửi `[PRIVATE den <target>]` (xác nhận).
- **Lệnh lạ bắt đầu bằng `/`**: trả lỗi, gợi ý `/help`.
- **Còn lại**: `Server.broadcast("[PUBLIC] " + username + ": " + message)`.

### 3.5. `validGroupName(String name)` – kiểm tra tên nhóm

```java
return name != null && name.matches("[A-Za-z0-9_]{3,20}");
```

Cùng quy tắc với username. Trả `false` nếu rỗng/null.

### 3.6. `removeEmptyGroup(String groupName)` – xóa nhóm rỗng

```java
if (members != null && members.isEmpty()
        && Server.groups.remove(groupName, members)) { ... }
```

- Chỉ xóa khi tập rỗng.
- `remove(key, value)` là **xóa có điều kiện**: chỉ xóa nếu ánh xạ hiện tại vẫn trỏ đúng tập đó. Điều này tránh xóa nhầm một nhóm mới được tạo lại cùng tên bởi thread khác.
- Khi xóa thành công → broadcast thông báo nhóm bị xóa.

### 3.7. `disconnect()` – dọn dẹp khi client rời

1. Nếu chưa đăng nhập (`username == null`) thì bỏ qua.
2. `Server.clients.remove(username, this)` — **xóa có điều kiện**, chỉ xóa nếu tên vẫn trỏ đúng kết nối này (tránh trường hợp user đã đăng nhập lại bằng kết nối mới).
3. Duyệt mọi nhóm, cố gắng `members.remove(this)`:
   - Nếu có trong nhóm → thông báo nhóm và gọi `removeEmptyGroup`.
4. Broadcast `[SERVER] <username> da roi chat.`
5. Ghi log, đặt `username = null`.

Vì `finally` luôn chạy, việc dọn dẹp diễn ra cả khi `/quit`, khi mất mạng, hay khi server đóng socket.

---

## 4. `Client.java` – chương trình phía người dùng

### 4.1. Hằng số

```java
private static final String DEFAULT_HOST = "localhost";
private static final int PORT = 5000;
```

- Mặc định kết nối `localhost`.
- Có thể ghi đè host bằng tham số dòng lệnh: `java client.Client 192.168.1.10`.

### 4.2. `main(String[] args)` – luồng chạy

1. **Xác định host**: `args.length > 0 ? args[0] : DEFAULT_HOST`.
2. In banner **MINI CHAT CLIENT**.
3. Mở tài nguyên bằng try-with-resources:
   - `Socket(host, PORT)` — kết nối tới server (ném `IOException` nếu thất bại).
   - `BufferedReader in` — đọc tin từ server.
   - `PrintWriter out` — gửi tin tới server (auto-flush).
   - `Scanner scanner` — đọc bàn phím.
4. In "Da ket noi Server: host:port".
5. Hỏi username, `trim()`, gửi dòng đầu tiên bằng `out.println(username)`.
6. **Đọc phản hồi đăng nhập** bằng `in.readLine()` trước khi tạo luồng nhận:
   - `null` → server đóng kết nối, thoát.
   - Không bắt đầu bằng `"OK "` → in lỗi, kết thúc chương trình.
   - Điều này đảm bảo client chỉ bắt đầu chat sau khi đăng nhập hợp lệ.
7. In hướng dẫn `/help`.
8. **Tạo luồng nhận** (`receiver`):
   ```java
   Thread receiver = new Thread(() -> {
       while ((message = in.readLine()) != null) {
           System.out.println();
           System.out.println(message);
           System.out.print("> ");
       }
   });
   receiver.setDaemon(true);
   receiver.start();
   ```
   - Luồng này liên tục in tin nhắn server đẩy về.
   - `setDaemon(true)` để JVM không chờ nó khi luồng chính kết thúc.
   - In lại dấu nhắc `> ` sau mỗi tin để người dùng biết có thể nhập tiếp.
9. **Luồng chính – vòng lặp nhập liệu:**
   ```java
   while (scanner.hasNextLine()) {
       System.out.print("> ");
       String message = scanner.nextLine();
       if (message.isBlank()) continue;
       out.println(message);
       if (message.equals("/quit")) break;
   }
   ```
   - Bỏ qua dòng trống.
   - Gửi nguyên dòng cho server; server tự phân tích lệnh.
   - `/quit` kết thúc vòng lặp; try-with-resources đóng socket, sau đó in "Client da ket thuc.".
10. **`catch (IOException)`**: in "Khong the ket noi Server.", chi tiết lỗi và gợi ý kiểm tra server/cổng.

### 4.3. Vì sao cần hai luồng ở client?

`readLine()` là thao tác **chặn** (blocking). Nếu dùng chung một luồng vừa đọc bàn phím vừa đọc server, client sẽ bị treo khi chờ tin nhắn và không nhập được. Tách luồng nhận riêng cho phép **vừa gõ vừa nhận tin** đồng thời.

---

## 5. Giao thức thông điệp

Dữ liệu là **văn bản, mỗi thông điệp kết thúc bằng newline** (dùng charset mặc định của JVM, thường là UTF-8). Không có header nhị phân, không độ dài tiền tố.

### Client → Server

| Nội dung | Ý nghĩa |
|----------|---------|
| `<username>` (dòng đầu tiên) | Đăng nhập |
| `<text>` | Chat chung |
| `/help`, `/users`, `/groups`, `/quit` | Lệnh |
| `/create <ten>` | Tạo nhóm |
| `/join <ten>` / `/leave <ten>` | Vào/rời nhóm |
| `/g <ten> <noi_dung>` | Chat nhóm |
| `/w <user> <noi_dung>` | Chat riêng |

### Server → Client

| Tiền tố | Ý nghĩa |
|---------|---------|
| `OK ...` | Đăng nhập thành công |
| `ERROR ...` | Đăng nhập thất bại (username sai/trùng) |
| `[HELP]`, `[USERS]`, `[GROUPS]`, `[INFO]`, `[ERROR]` | Phản hồi lệnh |
| `[PUBLIC] <user>: <text>` | Tin nhắn chung |
| `[PRIVATE tu <user>] ...` | Tin nhắn riêng nhận được |
| `[PRIVATE den <user>] ...` | Xác nhận tin nhắn riêng đã gửi |
| `[GROUP <ten>] ...` | Tin nhắn nhóm |
| `[SERVER] ...` | Thông báo hệ thống (join/leave/tạo/xóa nhóm) |

> Lưu ý: `Client` chỉ kiểm tra tiền tố `"OK "` khi đăng nhập; sau đó nó in mọi dòng nhận được, không phân tích thêm.

---

## 6. Các luồng hoạt động (workflows)

### 6.1. Đăng nhập

```
Client                      Server (ClientHandler)
  │  connect TCP               │
  │───────────────────────────►│ accept() → pool.execute(handler)
  │  "alice"                   │
  │───────────────────────────►│ readLine()
  │                            │ validate regex
  │                            │ putIfAbsent("alice", handler)
  │  "OK Chao mung alice!..."  │
  │◄───────────────────────────│
  │                            │ broadcastExcept("[SERVER] alice da tham gia chat.")
  │  bắt đầu chat              │
```

### 6.2. Chat chung (public)

1. Client gõ nội dung bất kỳ không bắt đầu bằng `/`.
2. Server nhận, gọi `broadcast("[PUBLIC] alice: ...")`.
3. `broadcast` duyệt mọi handler trong `clients` và gửi — **kể cả người gửi**.
4. Mỗi client in dòng nhận được.

### 6.3. Chat riêng (private)

1. `/w bob xin chao`
2. Server `split(" ", 3)` → target `bob`, content `xin chao`.
3. Kiểm tra `bob` online và không phải chính mình.
4. Gửi bob: `[PRIVATE tu alice] xin chao`.
5. Gửi lại alice: `[PRIVATE den bob] xin chao`.

### 6.4. Tạo nhóm

1. `/create team1`
2. Kiểm tra tên hợp lệ, tạo `Set` thành viên chứa người tạo.
3. `groups.putIfAbsent("team1", members)`.
4. Broadcast `[SERVER] alice da tao nhom team1.`

### 6.5. Tham gia nhóm

1. `/join team1`
2. Lấy tập thành viên; nếu không có → lỗi.
3. `members.add(this)`:
   - `true` → `sendToGroup` thông báo "alice da tham gia nhom".
   - `false` → `[INFO] Ban da o trong nhom team1.`

### 6.6. Rời nhóm và tự xóa nhóm rỗng

1. `/leave team1`
2. `members.remove(this)` thất bại → báo không phải thành viên.
3. Thành công → thông báo nhóm, `removeEmptyGroup("team1")`.
4. Nếu nhóm rỗng → xóa khỏi `groups`, broadcast "Nhom team1 da bi xoa...".

### 6.7. Ngắt kết nối

Kích hoạt khi: gõ `/quit`, đóng terminal, mất mạng, hoặc server đóng socket.

1. `readLine()` trả `null` (hoặc ném `IOException`).
2. Thoát vòng lặp, `finally` gọi `disconnect()`.
3. Xóa khỏi `clients`, xóa khỏi mọi nhóm, xóa nhóm rỗng.
4. Broadcast `[SERVER] alice da roi chat.`

### 6.8. Các trường hợp biên đã xử lý

| Tình huống | Cách xử lý |
|------------|-----------|
| Username sai định dạng | `ERROR` + đóng kết nối |
| Username trùng | `putIfAbsent` phát hiện → `ERROR` |
| Tin nhắn rỗng | `isBlank()` → bỏ qua |
| Lệnh không tồn tại | `[ERROR] Lenh khong hop le` |
| Chat nhóm khi chưa join | Kiểm tra `members.contains(this)` |
| Tự nhắn riêng mình | `target == this` → lỗi |
| Nhắn cho người offline | `clients.get(...) == null` → lỗi |
| Nhóm rỗng sau khi rời | Bị xóa tự động |
| Hai thread ghi cùng client | `synchronized (writer)` |

---

## 7. Ghi chú về đồng thời (concurrency)

| Cơ chế | Vị trí | Mục đích |
|--------|--------|----------|
| `ConcurrentHashMap` | `Server.clients`, `Server.groups` | Đọc/ghi an toàn giữa nhiều thread |
| `ConcurrentHashMap.newKeySet()` | tập thành viên nhóm | `Set` an toàn thread |
| `putIfAbsent` | đăng nhập, tạo nhóm | Giữ tính nguyên tử khi "kiểm tra rồi thêm" |
| `remove(key, value)` | disconnect, xóa nhóm | Xóa có điều kiện, tránh xóa nhầm |
| `synchronized (writer)` | `ClientHandler.send` | Tránh ghi xen kẽ cùng một client |
| `volatile` | `username`, `out` | Đảm bảo visibility giữa các thread |
| `ExecutorService` | `Server.pool` | Quản lý thread cho từng kết nối |

---

## 8. Hạn chế & hướng phát triển

- Không có xác thực/mật khẩu, không mã hóa TLS.
- Không lưu lịch sử tin nhắn hay trạng thái nhóm bền vững (cần DB).
- Chưa có GUI/WebSocket.
- Chưa giới hạn kích thước tin nhắn, chưa chống spam/flood.
- Có thể bổ sung: gửi file, trạng thái đang gõ, danh sách thành viên nhóm, tin nhắn offline.
