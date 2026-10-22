# Quy tắc lưu trữ ảnh: tên thư mục, tên file, ảnh RAW

> Tài liệu tham chiếu cho backend `travelPhoto-API`. Cập nhật lần cuối: 2026-10-22.
> Code liên quan: `storage/FolderNameUtils`, `storage/StorageFileNames`, `storage/RawImageSupport`,
> `storage/LocalFileStorageServiceImpl`, `service/impl/PhotoServiceImpl`.

## 1. Vị trí lưu trữ

| Môi trường | Biến cấu hình | Giá trị ví dụ |
|---|---|---|
| Dev (chạy `mvn spring-boot:run`, không Docker) | `STORAGE_ROOT_PATH` | `D:/MY PRO/applications/TravelPhoto/storage/photo` |
| Production (Docker trên Ubuntu) | `STORAGE_HOST_PATH` (vế trái `volumes:`) | `/media/mos/PhotoStorage/travelphoto/photo` |
| Production, **bên trong container** | `STORAGE_ROOT_PATH` | luôn là `/data/photo-storage` (compose ép cứng) |

- Ảnh nằm **ngoài** thư mục project/repo, mount vào container bằng bind mount.
- Dev và production dùng storage **tách biệt**. Không dùng chung 1 database giữa dev và production,
  vì bản ghi ảnh trỏ tới file chỉ tồn tại ở một máy (gây lỗi file không tìm thấy).
- Ổ NTFS (`/media/mos/PhotoStorage`): tên file không được chứa `: * ? " < > |` (xem mục 3).

## 2. Cấu trúc thư mục

```
{STORAGE_ROOT}/
  {username}_{userId6}/                       <- thư mục cha (mỗi user 1 thư mục), vd dautruong_000001
    {yyyyMMdd}_{TenSuKien}_{eventId6}/        <- thư mục event, vd 20261022_DaLat2026_000014
      IMG_0042.jpg                            <- ảnh thường (tên gốc)
      DSC_0001.NEF                            <- ảnh RAW gốc
      .previews/
        DSC_0001.NEF.jpg                      <- JPEG xem trước của file RAW
```

Quy tắc đặt tên thư mục (không đổi, xem `FolderNameUtils`):

- Thư mục cha: `{username}_{parentFolderCode}`
  - `username` = phần trước `@` của email, chỉ giữ `[a-zA-Z0-9_]`, rỗng thì `user`.
  - `parentFolderCode` = `users.id` đệm số 0 đủ 6 chữ số.
  - Tính **một lần** khi tạo event đầu tiên rồi lưu vào `users.storage_folder`, không đổi dù email đổi.
- Thư mục event: `{yyyyMMdd}_{EventName}_{eventFolderCode}`
  - `yyyyMMdd` = ngày bắt đầu event (`start_date`).
  - `EventName` = tên event bỏ dấu tiếng Việt, bỏ ký tự đặc biệt, viết PascalCase (`du lịch hà giang` → `DuLichHaGiang`),
    rỗng thì `Event`.
  - `eventFolderCode` = `events.id` đệm số 0 đủ 6 chữ số.
  - Lưu tên thư mục (không phải đường dẫn tuyệt đối) vào `events.storage_folder`.
- Ảnh **nằm trong thư mục của người upload**, không phải của chủ event (EDITOR upload vào event của người khác
  thì file nằm trong thư mục của EDITOR). Khi đọc/xóa ảnh dùng `photos.uploaded_by`.
- Đường dẫn trong DB luôn **tương đối** so với `STORAGE_ROOT`.

## 3. Quy tắc đặt tên FILE (áp dụng từ 2026-10-22)

**Giữ nguyên tên gốc** của file người dùng upload. Không còn tiền tố UUID như bản cũ (`{uuid}_{tên}`).

`StorageFileNames.sanitize(tên)`:

1. Chỉ lấy phần tên file, bỏ mọi đường dẫn (`../../a.jpg`, `C:\x\a.jpg` → `a.jpg`).
2. Chuẩn hóa Unicode NFC. **Giữ tiếng Việt có dấu** (`Ảnh đẹp Đà Lạt.jpg` giữ nguyên).
3. Thay ký tự cấm trên NTFS và ký tự điều khiển bằng `_`: `< > : " / \ | ? *`.
4. Bỏ dấu chấm/khoảng trắng ở cuối tên (NTFS không cho phép).
5. Tên Windows dành riêng (`CON`, `PRN`, `AUX`, `NUL`, `COM1-9`, `LPT1-9`) thêm tiền tố `_`.
6. Tên rỗng hoặc chỉ gồm dấu chấm → `file`.
7. Tối đa 200 ký tự, cắt phần tên nhưng **giữ đuôi** file.

Trùng tên trong cùng thư mục (`StorageFileNames.uniqueIn`): thêm hậu tố ` (1)`, ` (2)`... trước đuôi
(`a.jpg` → `a (1).jpg`). Trường hợp này chỉ xảy ra ở mức đĩa; ở mức nghiệp vụ, trùng
`original_name` trong cùng event vẫn bị từ chối (`PHOTO_EXIST`).

Tên hiển thị/tải về:

- `photos.original_name` giữ tên **đúng như người dùng upload** (chưa làm sạch), dùng để hiển thị và đặt tên khi tải.
- `photos.file_name` là tên thật trên đĩa (đã làm sạch, có thể có hậu tố `(n)`).
- Header tải về dùng `Content-Disposition: attachment; filename*=UTF-8''...` (RFC 6266), giữ đúng tên có dấu.

Ảnh cũ đã lưu theo quy tắc cũ (`{uuid}_{tên}`) **không bị đổi tên**; `photos.path` vẫn trỏ đúng file cũ.

## 4. Giới hạn upload

| Lớp | Giới hạn | Cấu hình |
|---|---|---|
| nginx (web) | 100 MB/request | `client_max_body_size` trong `web/nginx.conf` |
| Spring | 50 MB/file, 500 MB/request | `spring.servlet.multipart.*` trong `application.yml` |
| Nghiệp vụ | 50 MB/file, tối đa 100 file/request | `PhotoServiceImpl.MAX_SIZE`, `MAX_FILES` |
| Web | 50 MB/file (`MAX_FILE_SIZE`), chia lô ≤ 80 MB / ≤ 20 file mỗi request | `web/src/utils.ts`, `web/src/api/photos.ts` |

- Cloudflare gói miễn phí giới hạn khoảng 100 MB mỗi request, nên web upload theo **lô nhỏ**, không gửi tất cả cùng lúc.
- Định dạng ảnh thường được phép: `image/jpeg`, `image/png`, `image/webp` (kiểm tra theo `Content-Type`).
  HEIC chưa được backend hỗ trợ dù web cho chọn file.
- Dung lượng tính vào hạn mức người dùng (`storage_quota`) là **file gốc**; bản xem trước RAW không tính.

## 5. Ảnh RAW

Hỗ trợ đuôi: `.cr2 .cr3 .crw .nef .nrw .arw .srf .sr2 .dng .orf .rw2 .raf .pef .srw .3fr .iiq .rwl`
(`RawImageSupport.RAW_EXTENSIONS`, không phân biệt hoa thường).

Luồng upload (`PhotoServiceImpl.uploadMultiple`):

1. **Nhận diện** theo đuôi file. Trình duyệt thường gửi RAW với `Content-Type: application/octet-stream`
   nên không dùng Content-Type để quyết định.
2. **Kiểm tra chữ ký (magic bytes)** của 16 byte đầu, tránh file đổi đuôi:
   - TIFF little-endian `II*\0`, `IIRO`/`IIRS` (ORF), `IIU\0` (RW2) — CR2, NEF, ARW, DNG, PEF...
   - TIFF big-endian `MM\0*`
   - `FUJIFILM` (RAF)
   - ISO BMFF `....ftypcrx ` (CR3)
   - Không khớp → `INVALID_TYPE`.
3. **Lưu file RAW gốc** nguyên vẹn (tên theo mục 3).
4. **Tạo bản xem trước JPEG** (`RawImageSupport.writePreview`): quét file RAW tìm các JPEG nhúng sẵn
   (hầu hết RAW đều nhúng 1 JPEG lớn), chọn JPEG có diện tích lớn nhất, giảm xuống cạnh dài tối đa 2560 px,
   ghi JPEG chất lượng 0.85 vào `{thư mục event}/.previews/{tên-file-RAW}.jpg`.
   - Không giải mã dữ liệu RAW thật (không cần thư viện native).
   - Lỗi tạo preview **không làm hỏng upload**: ảnh vẫn được lưu, `preview_path` để trống, web hiện nhãn "RAW".
   - Hạn chế: không xử lý xoay theo EXIF Orientation của preview.
5. Ghi DB: `photos.preview_path` (path tương đối của preview), `width`/`height` lấy theo preview,
   `mime_type` là `application/octet-stream` nếu client không gửi.

API liên quan:

| Endpoint | Mô tả |
|---|---|
| `GET /api/photos/download/{id}` | Tải **file gốc** (RAW hay ảnh thường). Quyền: admin / chủ event / member / share token. |
| `GET /api/photos/{id}/preview` | Ảnh **để hiển thị**: RAW → JPEG xem trước; ảnh thường → chính file gốc. Cùng quy tắc quyền. |

`PhotoResponse` có thêm `previewUrl` (URL hiển thị) và `raw` (true nếu file gốc là RAW). Web hiển thị bằng `previewUrl`
và chỉ dùng `url` cho nút tải về.

Xóa ảnh RAW xóa cả file gốc lẫn file preview. Xóa event xóa cả thư mục (gồm `.previews`).

## 6. Migration liên quan

- `V8`: `users.storage_folder`, `events.event_date`
- `V9`: `events.start_date/end_date/location/cover_image_path`
- `V11`: `photos.preview_path VARCHAR(500) NULL`

## 7. Việc còn lại / ý tưởng

- Thumbnail thật cho ảnh thường (hiện `previewUrl` của ảnh thường trỏ về file gốc, ảnh 50 MB sẽ tải chậm).
- Xoay preview theo EXIF Orientation.
- Hỗ trợ HEIC ở backend hoặc bỏ HEIC khỏi web.
- Khi cần chạy nhiều instance backend hoặc dung lượng lớn: chuyển sang object storage (MinIO/S3/R2) qua `StorageService`.
