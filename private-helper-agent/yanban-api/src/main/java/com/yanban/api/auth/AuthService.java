package com.yanban.api.auth;

import com.yanban.api.security.JwtService;
import com.yanban.api.security.JwtUser;
import com.yanban.api.user.SysUser;
import com.yanban.api.user.SysUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final SysUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(SysUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        if (users.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        SysUser user = new SysUser(username, passwordEncoder.encode(request.password()));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在", ex);
        }
        return tokensFor(user.getId(), user.getUsername());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String username = normalizeUsername(request.username());
        SysUser user = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        return tokensFor(user.getId(), user.getUsername());
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshRequest request) {
        JwtUser jwtUser = jwtService.parseRefreshToken(request.refreshToken());
        SysUser user = users.findById(jwtUser.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "刷新令牌无效"));
        return tokensFor(user.getId(), user.getUsername());
    }

    private AuthResponse tokensFor(Long userId, String username) {
        return AuthResponse.bearer(
                jwtService.createAccessToken(userId, username),
                jwtService.createRefreshToken(userId, username),
                jwtService.accessTokenTtlSeconds()
        );
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim();
    }
}
