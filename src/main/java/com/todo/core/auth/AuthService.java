package com.todo.core.auth;

import com.todo.core.common.ApiException;
import com.todo.core.user.User;
import com.todo.core.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9_]{3,30}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResult register(String rawUsername, String rawEmail, String password) {
        String username = normalizeUsername(rawUsername);
        String email = normalizeEmail(rawEmail);
        requirePassword(password);
        if (userRepository.findByUsername(username).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "A user is already registered with this username");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "A user is already registered with this email");
        }
        try {
            User user = userRepository.create(username, email, passwordEncoder.encode(password));
            return new LoginResult(user, jwtService.createToken(user));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "A user is already registered with this username or email");
        }
    }

    public LoginResult login(String rawEmail, String password) {
        String email = normalizeEmail(rawEmail);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return new LoginResult(user, jwtService.createToken(user));
    }

    private String normalizeUsername(String rawUsername) {
        String username = rawUsername == null ? "" : rawUsername.trim().toLowerCase();
        if (!USERNAME.matcher(username).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "username must be 3-30 characters of lowercase letters, digits, or underscores");
        }
        return username;
    }

    private String normalizeEmail(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        if (email.length() > 255 || !EMAIL.matcher(email).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "email must be a valid email address");
        }
        return email;
    }

    private void requirePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "password must be at least 8 characters");
        }
    }

    public record LoginResult(User user, JwtService.Token token) {
    }
}
