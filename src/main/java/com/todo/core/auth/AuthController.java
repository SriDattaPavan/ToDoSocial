package com.todo.core.auth;

import com.todo.core.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        return LoginResponse.from(authService.register(request.username(), request.email(), request.password()));
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return LoginResponse.from(authService.login(request.email(), request.password()));
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 30) String username,
            @NotBlank @Size(max = 255) String email,
            @NotBlank @Size(max = 200) String password
    ) {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 255) String email,
            @NotBlank @Size(max = 200) String password
    ) {
    }

    public record UserResponse(long id, String username, String email) {
        static UserResponse from(User user) {
            return new UserResponse(user.id(), user.username(), user.email());
        }
    }

    public record LoginResponse(String accessToken, Instant expiresAt, UserResponse user) {
        static LoginResponse from(AuthService.LoginResult result) {
            return new LoginResponse(
                    result.token().value(),
                    result.token().expiresAt(),
                    UserResponse.from(result.user())
            );
        }
    }
}
