package top.aiolife.sso.api;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.sso.convertor.SsoApiConvertor;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.pojo.req.UserCreateReq;
import top.aiolife.sso.pojo.req.UserUpdateReq;
import top.aiolife.sso.pojo.vo.UserVO;
import top.aiolife.sso.service.IUserService;

@RestController
@RequestMapping("/user-center")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class UserCenterController {

    private final IUserService userService;

    @GetMapping("/list")
    public ApiResponse<PageResp<UserVO>> list(@QueryParams CommonQuery<top.aiolife.sso.pojo.query.UserQuery> query) {
        return ApiResponse.success(userService.getUserList(query));
    }

    @PostMapping
    public ApiResponse<Void> add(@Valid @RequestBody UserCreateReq userEntityReq) {
        UserEntity userEntity = SsoApiConvertor.INSTANCE.fromUserCreateReq(userEntityReq);
        userService.addUser(userEntity);
        return ApiResponse.success();
    }

    @PutMapping
    public ApiResponse<Void> update(@Valid @RequestBody UserUpdateReq userEntityReq) {
        UserEntity userEntity = SsoApiConvertor.INSTANCE.fromUserUpdateReq(userEntityReq);
        userService.updateUser(userEntity, userEntityReq.isAvatarFileIdSpecified());
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.success();
    }
}
