package edu.batchmaker.dto.common;

public record MessageResponse(boolean success, String message) {

    public static MessageResponse ok(String message) {
        return new MessageResponse(true, message);
    }
}
