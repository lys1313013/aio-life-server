package top.aiolife.sso.api;

import top.aiolife.core.query.QueryParams;
import cn.dev33.satoken.annotation.SaCheckRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.pojo.vo.UserVO;
import top.aiolife.sso.service.IUserService;

@RestController
@RequestMapping("/user-center")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class UserCenterController {

    private final IUserService userService;

    @GetMapping("/list")
    public ApiResponse<PageResp<UserVO>> list(@QueryParams CommonQuery query) {
        return ApiResponse.success(userService.getUserList(query));
    }

    @PostMapping
    public ApiResponse<Void> add(@RequestBody UserEntity userEntity) {
        userService.addUser(userEntity);
        return ApiResponse.success();
    }

    @PutMapping
    public ApiResponse<Void> update(@RequestBody UserEntity userEntity) {
        userService.updateUser(userEntity);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.success();
    }
}
