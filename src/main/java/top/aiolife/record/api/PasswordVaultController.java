package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.mapper.IPasswordVaultMapper;
import top.aiolife.record.pojo.entity.PasswordVaultEntity;
import top.aiolife.record.service.IPasswordVaultService;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 密码库控制器。
 * 注意：加解密全部在前端完成（SM4-GCM，密钥由用户主密码经 PBKDF2 派生），
 * 本控制器仅做密文的透传存取，后端不接触明文与密钥，不保证机密性
 *
 * @author Lys
 * @date 2026/04/28
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/password")
public class PasswordVaultController {

    private final IPasswordVaultService passwordVaultService;
    private final IPasswordVaultMapper passwordVaultMapper;

    /**
     * 查询密码列表
     */
    @GetMapping("/list")
    public ApiResponse<List<PasswordVaultEntity>> list() {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<PasswordVaultEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PasswordVaultEntity::getUserId, userId);
        wrapper.orderByDesc(PasswordVaultEntity::getUpdateTime);
        List<PasswordVaultEntity> list = passwordVaultMapper.selectList(wrapper);
        return ApiResponse.success(list);
    }

    /**
     * 获取单条密码详情
     */
    @GetMapping("/{id}")
    public ApiResponse<PasswordVaultEntity> getById(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<PasswordVaultEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PasswordVaultEntity::getId, id);
        wrapper.eq(PasswordVaultEntity::getUserId, userId);
        PasswordVaultEntity entity = passwordVaultMapper.selectOne(wrapper);
        return ApiResponse.success(entity);
    }

    /**
     * 新增密码
     */
    @PostMapping
    public ApiResponse<Boolean> save(@RequestBody PasswordVaultEntity entity) {
        entity.setUserId(StpUtil.getLoginIdAsLong());
        entity.setCreateUser(StpUtil.getLoginIdAsLong());
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        if (entity.getFavorite() == null) {
            entity.setFavorite(false);
        }
        if (entity.getCategory() == null || entity.getCategory().isEmpty()) {
            entity.setCategory("其他");
        }
        passwordVaultMapper.insert(entity);
        return ApiResponse.success(true);
    }

    /**
     * 编辑密码
     */
    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@PathVariable Long id, @RequestBody PasswordVaultEntity entity) {
        Long userId = StpUtil.getLoginIdAsLong();
        PasswordVaultEntity existing = passwordVaultMapper.selectById(id);
        if (existing == null || !userId.equals(existing.getUserId())) {
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, "记录不存在或无权限操作");
        }
        entity.setId(id);
        // 防止请求方篡改记录归属
        entity.setUserId(null);
        entity.setCreateUser(null);
        entity.setUpdateUser(userId);
        entity.setUpdateTime(LocalDateTime.now());
        passwordVaultMapper.updateById(entity);
        return ApiResponse.success(true);
    }

    /**
     * 删除密码
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<PasswordVaultEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PasswordVaultEntity::getId, id);
        wrapper.eq(PasswordVaultEntity::getUserId, userId);
        passwordVaultMapper.delete(wrapper);
        return ApiResponse.success(true);
    }

    /**
     * 获取分类列表
     */
    @GetMapping("/categories")
    public ApiResponse<List<String>> categories() {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<PasswordVaultEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PasswordVaultEntity::getUserId, userId);
        wrapper.select(PasswordVaultEntity::getCategory);
        wrapper.groupBy(PasswordVaultEntity::getCategory);
        List<PasswordVaultEntity> list = passwordVaultMapper.selectList(wrapper);

        java.util.Set<String> categorySet = new java.util.LinkedHashSet<>(Arrays.asList("工作", "生活", "学习", "金融", "社交", "游戏", "其他"));
        for (PasswordVaultEntity entity : list) {
            if (entity.getCategory() != null && !entity.getCategory().isEmpty()) {
                categorySet.add(entity.getCategory());
            }
        }
        return ApiResponse.success(new java.util.ArrayList<>(categorySet));
    }
}