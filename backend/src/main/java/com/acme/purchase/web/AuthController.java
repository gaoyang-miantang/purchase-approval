package com.acme.purchase.web;

import com.acme.purchase.common.ApiResult;
import com.acme.purchase.security.UserContext;
import com.acme.purchase.service.AuthService;
import com.acme.purchase.service.PurchaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResult<Dto.LoginResponse> login(@Valid @RequestBody Dto.LoginRequest request) {
        return ApiResult.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResult<Dto.UserView> me() {
        return ApiResult.ok(authService.toView(UserContext.require()));
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            authService.logout(authorization.substring(7));
        }
        return ApiResult.ok();
    }
}
