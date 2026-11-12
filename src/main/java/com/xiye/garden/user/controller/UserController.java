package com.xiye.garden.user.controller;

import com.xiye.garden.auth.security.CurrentUser;
import com.xiye.garden.auth.service.AuthService;
import com.xiye.garden.common.ApiResponse;
import com.xiye.garden.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final AuthService auth;
    private final CurrentUser currentUser;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserVO>> me() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(auth.user(currentUser.id())));
    }
}
