package edu.batchmaker.dto.auth;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        CurrentUserResponse user) {

    public static LoginResponse of(String token, long expiresInSeconds, CurrentUserResponse user) {
        return new LoginResponse(token, "Bearer", expiresInSeconds, user);
    }
}
