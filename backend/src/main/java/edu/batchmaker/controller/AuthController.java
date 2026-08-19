package edu.batchmaker.controller;

import edu.batchmaker.dto.auth.CurrentUserResponse;
import edu.batchmaker.dto.auth.LoginRequest;
import edu.batchmaker.dto.auth.LoginResponse;
import edu.batchmaker.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse me() {
        return authService.me();
    }
}
