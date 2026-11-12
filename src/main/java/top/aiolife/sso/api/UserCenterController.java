package top.aiolife.sso.api;

import io.swagger.v3.oas.annotations.Operation;

import cn.dev33.satoken.stp.StpUtil;

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
import top.aiolife.sso.pojo.query.UserQuery;
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

    @Operation(summary = "管理员分页查询用户")
    @GetMapping("/list")
    public ApiResponse<PageResp<UserVO>> list(@QueryParams CommonQuery<UserQuery> query) {
        return ApiResponse.success(userService.getUserList(query));
    }

    @Operation(summary = "管理员新增用户")
    @PostMapping
    public ApiResponse<Void> add(@Valid @RequestBody UserCreateReq userEntityReq) {
        UserEntity userEntity = SsoApiConvertor.INSTANCE.fromUserCreateReq(userEntityReq);
        userService.addUser(userEntity);
        return ApiResponse.success();
    }

    @Operation(summary = "管理员更新用户")
    @PutMapping
    public ApiResponse<Void> update(@Valid @RequestBody UserUpdateReq userEntityReq) {
        UserEntity userEntity = SsoApiConvertor.INSTANCE.fromUserUpdateReq(userEntityReq);
        userEntity.setUpdateUser(StpUtil.getLoginIdAsLong());
        userService.updateUser(userEntity, userEntityReq.isAvatarFileIdSpecified());
        return ApiResponse.success();
    }

    @Operation(summary = "管理员删除用户")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.success();
    }
}
