package com.travelalbum.storage;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Tạo thumbnail JPEG nhỏ (mặc định cạnh dài 480px) cho ảnh để hiển thị danh sách/thẻ album
 * nhanh, thay vì tải file gốc (có thể tới 50MB). Thumbnail lưu cạnh ảnh gốc trong ".thumbs" và
 * được tạo lười (lần đầu có người xem). Có áp dụng EXIF Orientation vì ảnh tái mã hóa mất EXIF.
 */
public final class ThumbnailSupport {

    public static final int DEFAULT_MAX_DIMENSION = 480;
    private static final float QUALITY = 0.8f;

    private ThumbnailSupport() {
    }

    /** "ev/a/IMG.jpg" -> "ev/a/.thumbs/IMG.jpg.jpg" (luôn đặt theo tên FILE GỐC của ảnh). */
    public static String thumbPathFor(String originalRelativePath) {
        String p = originalRelativePath.replace('\\', '/');
        int slash = p.lastIndexOf('/');
        String dir = slash >= 0 ? p.substring(0, slash + 1) : "";
        String name = slash >= 0 ? p.substring(slash + 1) : p;
        return dir + ".thumbs/" + name + ".jpg";
    }

    /**
     * Đọc {@code source}, thu nhỏ, xoay theo {@code orientation} (EXIF, có thể null) rồi ghi JPEG vào
     * {@code target}. Trả về false nếu không đọc được ảnh (định dạng ImageIO không hỗ trợ, file hỏng).
     */
    public static boolean create(Path source, Path target, Integer orientation, int maxDimension) {
        try (ImageInputStream in = ImageIO.createImageInputStream(source.toFile())) {
            if (in == null) {
                return false;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return false;
            }
            ImageReader reader = readers.next();
            BufferedImage image;
            try {
                reader.setInput(in, true, true);
                int longest = Math.max(reader.getWidth(0), reader.getHeight(0));
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, longest / (maxDimension * 2)); // giảm mẫu thô, giữ dư để scale mượt
                if (step > 1) {
                    param.setSourceSubsampling(step, step, 0, 0);
                }
                image = reader.read(0, param);
            } finally {
                reader.dispose();
            }
            if (image == null) {
                return false;
            }
            BufferedImage out = rotate(scale(image, maxDimension), orientation);
            Files.createDirectories(target.getParent());
            writeJpeg(out, target);
            return true;
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    private static BufferedImage scale(BufferedImage src, int maxDimension) {
        int w = src.getWidth();
        int h = src.getHeight();
        double ratio = Math.min(1.0, (double) maxDimension / Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * ratio));
        int nh = Math.max(1, (int) Math.round(h * ratio));
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, nw, nh, java.awt.Color.WHITE, null); // nền trắng cho PNG trong suốt
        } finally {
            g.dispose();
        }
        return dst;
    }

    /** Áp dụng EXIF Orientation: 3 = 180°, 6 = 90° cw, 8 = 90° ccw (kèm lật cho 2,4,5,7). */
    static BufferedImage rotate(BufferedImage src, Integer orientation) {
        if (orientation == null || orientation == 1) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5 && orientation <= 8;
        BufferedImage dst = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int nx;
                int ny;
                switch (orientation) {
                    case 2 -> { nx = w - 1 - x; ny = y; }
                    case 3 -> { nx = w - 1 - x; ny = h - 1 - y; }
                    case 4 -> { nx = x; ny = h - 1 - y; }
                    case 5 -> { nx = y; ny = x; }
                    case 6 -> { nx = h - 1 - y; ny = x; }
                    case 7 -> { nx = h - 1 - y; ny = w - 1 - x; }
                    case 8 -> { nx = y; ny = w - 1 - x; }
                    default -> { nx = x; ny = y; }
                }
                dst.setRGB(nx, ny, rgb);
            }
        }
        return dst;
    }

    private static void writeJpeg(BufferedImage image, Path target) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (OutputStream os = Files.newOutputStream(target);
             ImageOutputStream out = ImageIO.createImageOutputStream(os)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
