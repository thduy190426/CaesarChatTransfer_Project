package com.tcpchat.common.model;

/**
 * Model đại diện cho thông tin file header trước khi gửi byte nhị phân hoặc File ACK.
 */
public class FilePacket {
    private String type; // Thường là FILE hoặc FILE_ACK
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private String status;
    private String savedPath;

    public FilePacket() {
    }

    public FilePacket(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSavedPath() {
        return savedPath;
    }

    public void setSavedPath(String savedPath) {
        this.savedPath = savedPath;
    }
}
