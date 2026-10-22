-- Chủ album bật "Công khai": mọi người đã đăng nhập xem được album (chỉ đọc).
ALTER TABLE events ADD COLUMN is_public TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1 = album công khai cho người đã đăng nhập';
