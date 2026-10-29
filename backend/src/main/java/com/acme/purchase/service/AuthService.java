package com.acme.purchase.service;

import com.acme.purchase.common.BizException;
import com.acme.purchase.domain.User;
import com.acme.purchase.repo.UserRepository;
import com.acme.purchase.security.TokenStore;
import com.acme.purchase.web.Dto;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final TokenStore tokenStore;

    public AuthService(UserRepository userRepo, TokenStore tokenStore) {
        this.userRepo = userRepo;
        this.tokenStore = tokenStore;
    }

    public Dto.LoginResponse login(Dto.LoginRequest req) {
        User user = userRepo.findByUsername(req.username().trim())
                // 不区分"用户不存在"和"密码错误"，避免被用来枚举账号
                .orElseThrow(() -> BizException.badRequest("用户名或密码错误"));
        if (!user.getPassword().equals(req.password())) {
            throw BizException.badRequest("用户名或密码错误");
        }
        String token = tokenStore.issue(user.getId());
        return new Dto.LoginResponse(token, toView(user));
    }

    public Dto.UserView toView(User user) {
        return new Dto.UserView(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getDepartment(), user.getRole().name(), user.getRole().getLabel(),
                user.getDepartment() + " · " + user.getRole().getLabel());
    }

    public void logout(String token) {
        tokenStore.revoke(token);
    }
}
