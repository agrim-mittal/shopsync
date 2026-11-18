package com.shopsync.protection;

import com.shopsync.util.JsonSerializable;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** An invoice, warranty card or other file a user attached to an order. */
public class StoredDocument implements JsonSerializable {

    public enum Kind { INVOICE, WARRANTY, OTHER }

    private final String id;
    private final String userId;
    private final String orderId;
    private final Kind kind;
    private final String fileName;
    private final String mimeType;
    private final byte[] data;
    private final LocalDateTime uploadedAt = LocalDateTime.now();

    public StoredDocument(String id, String userId, String orderId, Kind kind, String fileName, String mimeType, byte[] data) {
        this.id = id;
        this.userId = userId;
        this.orderId = orderId;
        this.kind = kind;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.data = data;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getOrderId() { return orderId; }
    public Kind getKind() { return kind; }
    public String getFileName() { return fileName; }
    public String getMimeType() { return mimeType; }
    public byte[] getData() { return data; }

    /** Metadata only; the file itself is downloaded separately. */
    @Override
    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("orderId", orderId);
        m.put("kind", kind);
        m.put("fileName", fileName);
        m.put("mimeType", mimeType);
        m.put("sizeKb", Math.max(1, Math.round(data.length / 1024.0)));
        m.put("uploadedAt", uploadedAt.toString());
        return m;
    }
}
