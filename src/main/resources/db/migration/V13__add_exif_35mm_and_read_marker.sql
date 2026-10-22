-- Tiêu cự quy đổi 35mm (vd điện thoại: 26 mm) + mốc đã đọc EXIF để đọc bù ảnh cũ đúng 1 lần.
ALTER TABLE photos ADD COLUMN focal_length_35mm VARCHAR(20) NULL COMMENT 'Tiêu cự quy đổi 35mm, vd 26 mm';
ALTER TABLE photos ADD COLUMN exif_read_at DATETIME NULL COMMENT 'Thời điểm đã đọc EXIF (NULL = chưa đọc, sẽ được đọc bù khi backend khởi động)';
