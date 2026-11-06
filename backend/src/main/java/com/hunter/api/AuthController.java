package com.hunter.api;

import com.hunter.model.DemoUser;
import com.hunter.repository.DemoUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final DemoUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final CookieCsrfTokenRepository csrfTokens;

    public AuthController(
            DemoUserRepository users,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfTokens
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokens = csrfTokens;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getToken());
    }

    @PostMapping("/signup")
    @Transactional
    public ResponseEntity<ApiModels.UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        String displayName = request.displayName().strip();
        if (email.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must be 80 characters or fewer.");
        }
        if (passwordBytes(request.password()) > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be 72 UTF-8 bytes or fewer.");
        }
        if (users.existsByUsername(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }

        DemoUser user = new DemoUser(
                UUID.randomUUID().toString(),
                email,
                displayName,
                passwordEncoder.encode(request.password()),
                "REVIEWER",
                "HUNTER workspace",
                Instant.now()
        );
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(users.saveAndFlush(user)));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.", exception);
        }
    }

    @PostMapping("/login")
    public ApiModels.UserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.email().strip().toLowerCase(Locale.ROOT),
                            request.password()
                    )
            );
        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        }

        sessionAuthenticationStrategy.onAuthentication(authentication, servletRequest, servletResponse);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        return toResponse(findUser(authentication.getName()));
    }

    @GetMapping("/me")
    public ApiModels.UserResponse currentUser(Authentication authentication) {
        return toResponse(findUser(authentication.getName()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        csrfTokens.saveToken(null, request, response);
        return ResponseEntity.noContent().build();
    }

    private DemoUser findUser(String email) {
        return users.findByUsername(email)
                .orElseThrow(() -> new AuthenticationServiceException("The authenticated account no longer exists."));
    }

    private static ApiModels.UserResponse toResponse(DemoUser user) {
        return new ApiModels.UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.getWorkspaceName()
        );
    }

    private static int passwordBytes(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length;
    }

    public record SignupRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 140) String displayName,
            @NotBlank @Size(min = 12, max = 72) String password
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password
    ) {
    }

    public record CsrfResponse(String token) {
    }
}
