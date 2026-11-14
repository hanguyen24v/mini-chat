# MiniChat

Ứng dụng chat nhiều người dùng đơn giản viết bằng **Java Socket (TCP)**, giao diện dòng lệnh (console). Server trung tâm quản lý người dùng, nhóm chat và chuyển tiếp tin nhắn giữa các client.

## Tính năng

| Tính năng | Mô tả |
|-----------|-------|
| Đăng nhập | Mỗi client chọn username (3–20 ký tự: chữ, số, `_`). Username phải là duy nhất. |
| Chat chung (public) | Gõ tin nhắn bình thường để gửi tới **tất cả** người đang online. |
| Chat riêng (private) | `/w <username> <nội dung>` gửi tin nhắn riêng cho một người. |
| Quản lý nhóm | Tạo (`/create`), tham gia (`/join`), rời (`/leave`) nhóm. |
| Chat nhóm | `/g <tên_nhóm> <nội dung>` gửi tin nhắn tới các thành viên nhóm. |
| Danh sách | `/users` xem người online, `/groups` xem các nhóm hiện có. |
| Thông báo hệ thống | Server tự thông báo khi có người vào/ra, tạo nhóm, nhóm bị xóa… |
| Dọn dẹp | Khi client ngắt kết nối, server tự xóa khỏi danh sách user và các nhóm. Nhóm rỗng bị xóa. |
| Đa luồng | Server dùng thread pool (`CachedThreadPool`) để phục vụ nhiều client đồng thời. |

## Công nghệ

- **Java SE** (project Eclipse cấu hình `JavaSE-25`, code chỉ dùng API cơ bản nên chạy được với JDK 17+).
- `java.net.ServerSocket` / `java.net.Socket` cho giao tiếp TCP.
- `ExecutorService` / `ConcurrentHashMap` cho xử lý đồng thời an toàn.
- Không dùng thư viện ngoài, không cần Maven/Gradle.

## Cấu trúc dự án

```
mini-chat/
├── src/
│   ├── server/
│   │   ├── Server.java          # Điểm khởi động server, quản lý danh sách user/nhóm
│   │   └── ClientHandler.java   # Xử lý một kết nối client, đọc & xử lý lệnh chat
│   └── client/
│       └── Client.java          # Chương trình client (console)
├── bin/                         # File .class đã biên dịch (output của Eclipse)
├── .classpath / .project        # Cấu hình project Eclipse
├── README.md
├── CODE_EXPLANATION.md          # Giải thích code, hàm, luồng hoạt động
└── RUNNING_GUIDE.md             # Hướng dẫn chạy chi tiết
```

## Bắt đầu nhanh

Yêu cầu: cài **JDK 17 trở lên** (`javac`, `java`).

```bash
# 1. Biên dịch
javac -d bin src/server/*.java src/client/*.java

# 2. Chạy server (cửa sổ 1)
java -cp bin server.Server

# 3. Chạy client (cửa sổ 2, 3, ...)
java -cp bin client.Client

# 4. Kết nối tới server ở máy khác
java -cp bin client.Client 192.168.1.10
```

Xem hướng dẫn đầy đủ (Eclipse, nhiều client, mạng LAN, xử lý lỗi) tại [`RUNNING_GUIDE.md`](RUNNING_GUIDE.md).

## Danh sách lệnh

| Lệnh | Chức năng |
|------|-----------|
| `/help` | Hiển thị danh sách lệnh |
| `/users` | Danh sách người dùng đang online |
| `/groups` | Danh sách nhóm hiện có |
| `/create <tên_nhóm>` | Tạo nhóm mới (tự động tham gia) |
| `/join <tên_nhóm>` | Tham gia nhóm có sẵn |
| `/leave <tên_nhóm>` | Rời nhóm |
| `/g <tên_nhóm> <nội dung>` | Gửi tin nhắn tới nhóm |
| `/w <username> <nội dung>` | Gửi tin nhắn riêng |
| `/quit` | Thoát chương trình |
| `<nội dung>` | Gửi tin nhắn chung cho mọi người |

Tên nhóm có cùng quy tắc với username: 3–20 ký tự chữ, số hoặc `_`.

## Định dạng thông điệp

Server và client trao đổi **văn bản thuần, mỗi thông điệp một dòng** (`\n` kết thúc):

```
OK Chao mung <username>! Ban da ket noi thanh cong.     # đăng nhập thành công
ERROR <lý do>                                            # đăng nhập thất bại
[PUBLIC] <người gửi>: <nội dung>                         # tin nhắn chung
[SERVER] ...                                             # thông báo hệ thống
[USERS] ... / [GROUPS] ... / [HELP] ...
[GROUP <tên>] ...                                        # tin nhắn nhóm
[PRIVATE tu <người gửi>] ... / [PRIVATE den <người nhận>] ...
```

## Giới hạn hiện tại

- Không có mật khẩu / xác thực — chỉ dựa vào username.
- Không lưu lịch sử tin nhắn; không có chat riêng khi người nhận offline.
- Không mã hóa (dữ liệu truyền dạng plain text).
- Chỉ hỗ trợ console, chưa có GUI/web.
- Danh sách nhóm nằm trong bộ nhớ server, mất khi server dừng.

## Tài liệu khác

- [`CODE_EXPLANATION.md`](CODE_EXPLANATION.md) – giải thích từng class, hàm, cấu trúc dữ liệu và các luồng xử lý.
- [`RUNNING_GUIDE.md`](RUNNING_GUIDE.md) – các bước chạy ứng dụng chi tiết.
