package com.hunter.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.UUID;

@Service
public class UploadStorageService {

    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 40_000_000L;
    private final Path storageRoot;

    public UploadStorageService(@Value("${hunter.storage.directory}") String storageDirectory) throws IOException {
        this.storageRoot = Path.of(storageDirectory).toAbsolutePath().normalize();
        Files.createDirectories(storageRoot);
    }

    public StoredImage store(MultipartFile image) throws IOException {
        ValidatedImage validated = validateForProcessing(image);
        String extension = extensionFor(validated.mediaType());
        String storageKey = UUID.randomUUID() + extension;
        Path destination = resolveStorageKey(storageKey);
        Files.write(destination, validated.bytes());
        return new StoredImage(storageKey, validated.mediaType(), sanitizeOriginalName(image.getOriginalFilename()));
    }

    public static String extensionFor(String mediaType) {
        return switch (mediaType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            default -> throw new IllegalArgumentException("Unsupported image media type: " + mediaType);
        };
    }

    public ValidatedImage validateForProcessing(MultipartFile image) throws IOException {
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a product label image.");
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Images must be 10 MB or smaller.");
        }

        String contentType = image.getContentType();
        if (!"image/png".equals(contentType) && !"image/jpeg".equals(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Use a PNG or JPEG label image.");
        }

        byte[] content = image.getBytes();
        validateImage(content, contentType);
        return new ValidatedImage(content, contentType);
    }

    public Path resolveStorageKey(String storageKey) {
        if (storageKey == null || !storageKey.matches("[a-f0-9-]{36}\\.(png|jpg)")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found.");
        }
        Path path = storageRoot.resolve(storageKey).normalize();
        if (!path.getParent().equals(storageRoot)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found.");
        }
        return path;
    }

    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolveStorageKey(storageKey));
    }

    private static void validateImage(byte[] content, String contentType) throws IOException {
        boolean validSignature = "image/png".equals(contentType)
                ? content.length >= 8 && content[0] == (byte) 0x89 && content[1] == 0x50
                    && content[2] == 0x4e && content[3] == 0x47 && content[4] == 0x0d
                    && content[5] == 0x0a && content[6] == 0x1a && content[7] == 0x0a
                : content.length >= 3 && content[0] == (byte) 0xff && content[1] == (byte) 0xd8
                    && content[2] == (byte) 0xff;
        if (!validSignature) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file content does not match a supported image format.");
        }

        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (stream == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is not a readable image.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is not a readable image.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image dimensions exceed the supported limit.");
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private static String sanitizeOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "label-image";
        }
        String fileName = Path.of(originalName.replace('\\', '/')).getFileName().toString();
        String sanitized = fileName.replaceAll("[^A-Za-z0-9._ -]", "_");
        return sanitized.substring(0, Math.min(sanitized.length(), 120));
    }

    public record StoredImage(String storageKey, String mediaType, String originalName) {
    }

    public record ValidatedImage(byte[] bytes, String mediaType) {
    }
}
