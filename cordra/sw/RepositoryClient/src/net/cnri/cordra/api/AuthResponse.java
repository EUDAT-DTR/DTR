package net.cnri.cordra.api;

public class AuthResponse {
    public boolean success = false;
    public String userId;
    public String username;
    
    public AuthResponse(boolean success, String userId, String username) {
        this.success = success;
        this.userId = userId;
        this.username = username;
    }
}