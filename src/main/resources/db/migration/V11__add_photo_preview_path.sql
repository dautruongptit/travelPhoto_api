-- Ảnh RAW: lưu file RAW gốc + 1 bản xem trước JPEG (xem docs/STORAGE_NAMING.md)
ALTER TABLE photos ADD COLUMN preview_path VARCHAR(500) NULL
  COMMENT 'Path tương đối của bản xem trước JPEG (chỉ có với ảnh RAW) — không lưu path tuyệt đối';
