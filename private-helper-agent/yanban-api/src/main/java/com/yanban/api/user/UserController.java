package com.yanban.api.user;

import com.yanban.api.security.JwtUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @GetMapping("/me")
    public UserMeResponse me(@AuthenticationPrincipal JwtUser currentUser) {
        return new UserMeResponse(currentUser.id(), currentUser.username());
    }
}
