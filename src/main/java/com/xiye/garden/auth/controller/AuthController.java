package com.xiye.garden.auth.controller;

import com.xiye.garden.auth.dto.*;
import com.xiye.garden.auth.security.*;
import com.xiye.garden.auth.service.*;
import com.xiye.garden.common.ApiResponse;
import com.xiye.garden.user.vo.UserVO;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    private final org.springframework.security.authentication.AuthenticationManager authenticationManager;
    private final EmailVerificationService verification;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository contexts;

    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<Map<String, String>>> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(Map.of("headerName", token.getHeaderName(), "token", token.getToken())));
    }

    @PostMapping("/email-codes")
    public ApiResponse<Void> send(@Valid @RequestBody EmailCodeDTO request, HttpServletRequest http) {
        verification.send(EmailAddress.normalize(request.email()), request.purpose(), http.getRemoteAddr());
        return new ApiResponse<>("OK", "如可处理，验证码将发送至该邮箱，请查收", null);
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserVO>> register(@Valid @RequestBody RegisterDTO request, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(auth.register(request, http.getRemoteAddr())));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserVO>> login(@Valid @RequestBody LoginDTO request, HttpServletRequest http,
                                                   HttpServletResponse response) {
        var loginRequest = UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password());
        loginRequest.setDetails(new org.springframework.security.web.authentication.WebAuthenticationDetailsSource().buildDetails(http));
        var authentication = authenticationManager.authenticate(loginRequest);
        var principal = (GardenPrincipal) authentication.getPrincipal();
        sessionStrategy.onAuthentication(authentication, http, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, http, response);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(auth.user(principal.userId())));
    }

    @PostMapping("/password/reset")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetPasswordDTO request, HttpServletRequest http) {
        auth.resetPassword(request, http.getRemoteAddr());
        var session = http.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ApiResponse.success(null);
    }
}
