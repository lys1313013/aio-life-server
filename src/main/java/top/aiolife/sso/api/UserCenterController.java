package top.aiolife.sso.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.sso.convertor.SsoApiConvertor;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.pojo.query.UserQuery;
import top.aiolife.sso.pojo.req.UserCreateReq;
import top.aiolife.sso.pojo.req.UserUpdateReq;
import top.aiolife.sso.pojo.vo.UserVO;
import top.aiolife.sso.service.IUserService;

/**
 * 用户管理控制器
 *
 * @author Lys
 * @date 2026/11/13
 */
@Tag(name = "用户管理")
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
    public ApiResponse<Void> update(@CurrentUserId String currentUserId,
                                    @Valid @RequestBody UserUpdateReq userEntityReq) {
        UserEntity userEntity = SsoApiConvertor.INSTANCE.fromUserUpdateReq(userEntityReq);
        userEntity.setUpdateUser(IdCodec.decode(currentUserId));
        userService.updateUser(userEntity, userEntityReq.isAvatarFileIdSpecified());
        return ApiResponse.success();
    }

    @Operation(summary = "管理员删除用户")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        userService.deleteUser(IdCodec.decode(id));
        return ApiResponse.success();
    }
}
