-- EXIF thật của ảnh, đọc lúc upload (xem docs/STORAGE_NAMING.md). Tất cả đều NULL được:
-- ảnh cũ / ảnh không có EXIF vẫn hợp lệ.
ALTER TABLE photos ADD COLUMN camera_model VARCHAR(120) NULL COMMENT 'Hãng + tên máy, vd Canon EOS R6';
ALTER TABLE photos ADD COLUMN lens_model   VARCHAR(120) NULL COMMENT 'Ống kính';
ALTER TABLE photos ADD COLUMN focal_length VARCHAR(20)  NULL COMMENT 'Tiêu cự, vd 35 mm';
ALTER TABLE photos ADD COLUMN aperture     VARCHAR(20)  NULL COMMENT 'Khẩu độ, vd f/2.8';
ALTER TABLE photos ADD COLUMN shutter_speed VARCHAR(30) NULL COMMENT 'Tốc độ màn trập, vd 1/500 sec';
ALTER TABLE photos ADD COLUMN iso          INT          NULL COMMENT 'ISO';
ALTER TABLE photos ADD COLUMN taken_at     DATETIME     NULL COMMENT 'Thời điểm chụp (EXIF DateTimeOriginal, giờ địa phương của máy ảnh)';
