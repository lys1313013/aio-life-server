package top.aiolife.record.api;

import top.aiolife.core.lock.StorageObjectLock;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import top.aiolife.config.CbtiConfig;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.ICbtiPersonalityMapper;
import top.aiolife.record.pojo.entity.CbtiPersonalityEntity;
import top.aiolife.record.pojo.req.CbtiPersonalitySaveReq;
import top.aiolife.record.pojo.vo.CbtiPersonalityAdminVO;
import top.aiolife.record.config.CbtiImageInitUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CBTI 人格管理控制器
 *
 * <p>用途：管理员对 CBTI 人格类型进行新增/编辑/删除，并上传/替换角色图片。</p>
 *
 * @author Ethan
 * @date 2026/04/19
 */
@RestController
@RequestMapping("/cbti/admin")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class CbtiAdminController {

    private final ICbtiPersonalityMapper cbtiPersonalityMapper;

    private final ObjectMapper objectMapper;

    private final MinioUtil minioUtil;

    private final MinioConfig minioConfig;

    private final CbtiConfig cbtiConfig;

    private final CbtiImageInitUtil cbtiImageInitUtil;
    private final StorageObjectLock objectLock;

    @Value("${aio.life.server.base-url}")
    private String serveBaseUrl;

    /**
     * 获取 CBTI 人格列表（管理端）。
     *
     * @return 统一返回结构，data 为人格列表（含 imageUrl）
     */
    @GetMapping("/personalities")
    public ApiResponse<List<CbtiPersonalityAdminVO>> list() {
        LambdaQueryWrapper<CbtiPersonalityEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CbtiPersonalityEntity::getIsDeleted, 0);
        wrapper.orderByAsc(CbtiPersonalityEntity::getIsSpecial);
        wrapper.orderByAsc(CbtiPersonalityEntity::getCode);
        List<CbtiPersonalityEntity> list = cbtiPersonalityMapper.selectList(wrapper);
        return ApiResponse.success(list.stream().map(this::toAdminVO).toList());
    }

    /**
     * 新增 CBTI 人格类型。
     *
     * @param req 人格信息请求体
     * @return 统一返回结构，data 为新增后的人格详情
     */
    @PostMapping("/personalities")
    public ApiResponse<CbtiPersonalityAdminVO> create(@RequestBody CbtiPersonalitySaveReq req) {
        try {
            validateSaveReq(req, false);

            String code = normalizeCode(req.getCode());
            if (existsCode(code, null)) {
                return ApiResponse.error(ResponseCodeConst.PARAM_FAIL, "code 已存在");
            }

            long userId = StpUtil.getLoginIdAsLong();
            CbtiPersonalityEntity entity = new CbtiPersonalityEntity();
            fillEntity(entity, req, code);
            entity.fillCreateCommonField(userId);
            cbtiPersonalityMapper.insert(entity);
            return ApiResponse.success(toAdminVO(entity));
        } catch (Exception e) {
            return ApiResponse.error(ResponseCodeConst.COMMON_FAIL, "新增失败: " + e.getMessage());
        }
    }

    /**
     * 更新 CBTI 人格类型。
     *
     * @param id 人格记录ID
     * @param req 人格信息请求体
     * @return 统一返回结构，data 为更新后的人格详情
     */
    @PutMapping("/personalities/{id}")
    public ApiResponse<CbtiPersonalityAdminVO> update(@PathVariable Long id, @RequestBody CbtiPersonalitySaveReq req) {
        try {
            if (id == null) {
                return ApiResponse.error(ResponseCodeConst.PARAM_FAIL, "id 不能为空");
            }

            validateSaveReq(req, true);

            CbtiPersonalityEntity exist = cbtiPersonalityMapper.selectById(id);
            if (exist == null || !Objects.equals(exist.getIsDeleted(), 0)) {
                return ApiResponse.error(ResponseCodeConst.PARAM_FAIL, "记录不存在");
            }

            String code = normalizeCode(req.getCode());
            if (StringUtils.hasText(code) && existsCode(code, id)) {
                return ApiResponse.error(ResponseCodeConst.PARAM_FAIL, "code 已存在");
            }

            long userId = StpUtil.getLoginIdAsLong();
            fillEntity(exist, req, StringUtils.hasText(code) ? code : exist.getCode());
            exist.fillUpdateCommonField(userId);
            cbtiPersonalityMapper.updateById(exist);
            return ApiResponse.success(toAdminVO(exist));
        } catch (Exception e) {
            return ApiResponse.error(ResponseCodeConst.COMMON_FAIL, "更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除 CBTI 人格类型（逻辑删除）。
     *
     * @param id 人格记录ID
     * @return 统一返回结构，data 为是否删除成功
     */
    @DeleteMapping("/personalities/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        if (id == null) {
            return ApiResponse.error(ResponseCodeConst.PARAM_FAIL, "id 不能为空");
        }
        CbtiPersonalityEntity exist = cbtiPersonalityMapper.selectById(id);
        if (exist == null || !Objects.equals(exist.getIsDeleted(), 0)) {
            return ApiResponse.success(true);
        }
        long userId = StpUtil.getLoginIdAsLong();
        exist.setIsDeleted(1);
        exist.fillUpdateCommonField(userId);
        cbtiPersonalityMapper.updateById(exist);
        return ApiResponse.success(true);
    }

    /**
     * 上传/替换 CBTI 人格图片。
     *
     * <p>用途：管理员为指定人格 code 上传图片，服务端按 code 固定命名并覆盖同名对象。</p>
     *
     * @param code 人格代码
     * @param file 上传图片文件（multipart/form-data，字段名为 file）
     * @return 统一返回结构，data 包含 imageObject 与 imageUrl
     */
    @PostMapping("/personalities/{code}/image")
    public ApiResponse<Map<String, Object>> uploadImage(@PathVariable String code, @RequestParam("file") MultipartFile file) {
        if (!StringUtils.hasText(code)) {
            return ApiResponse.error(ResponseCodeConst.PARAM_FAIL, "code 不能为空");
        }
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(ResponseCodeConst.PARAM_FAIL, "文件不能为空");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !contentType.startsWith("image/")) {
            return ApiResponse.error(ResponseCodeConst.PARAM_FAIL, "仅支持图片文件");
        }

        String normalizedCode = normalizeCode(code);
        CbtiPersonalityEntity exist = getByCode(normalizedCode);
        if (exist == null) {
            return ApiResponse.error(ResponseCodeConst.PARAM_FAIL, "人格不存在");
        }

        String ext = detectExt(file.getOriginalFilename(), contentType);
        String objectPrefix = normalizePrefix(cbtiConfig.getObjectPrefix());
        String objectName = objectPrefix + normalizedCode + "." + ext;
        String bucketName = resolveBucketName();

        // 本方法无外层事务，Mapper 更新返回前已提交；锁覆盖对象写入与引用更新。
        try (var lease = objectLock.acquire(bucketName, objectName)) {
            minioUtil.uploadFile(bucketName, file, objectName);
            long userId = StpUtil.getLoginIdAsLong();
            exist.setImageObject(objectName);
            exist.fillUpdateCommonField(userId);
            cbtiPersonalityMapper.updateById(exist);

            Map<String, Object> data = new HashMap<>();
            data.put("imageObject", objectName);
            data.put("imageUrl", buildPreviewUrl(bucketName, objectName));
            return ApiResponse.success(data);
        } catch (Exception e) {
            return ApiResponse.error(ResponseCodeConst.COMMON_FAIL, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 手动触发初始化 CBTI 人格图片。
     *
     * @return 统一返回结构，data 为是否触发成功
     */
    @PostMapping("/personalities/init-images")
    public ApiResponse<Boolean> initImages() {
        try {
            cbtiImageInitUtil.initImages();
            return ApiResponse.success(true);
        } catch (Exception e) {
            return ApiResponse.error(ResponseCodeConst.COMMON_FAIL, "初始化图片失败: " + e.getMessage());
        }
    }

    private void validateSaveReq(CbtiPersonalitySaveReq req, boolean isUpdate) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (!isUpdate) {
            if (!StringUtils.hasText(req.getCode())) {
                throw new IllegalArgumentException("code 不能为空");
            }
        }
        if (StringUtils.hasText(req.getCode()) && req.getCode().length() > 20) {
            throw new IllegalArgumentException("code 长度不能超过20");
        }
        if (!StringUtils.hasText(req.getName())) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (req.getVector() == null || req.getVector().size() != 15) {
            throw new IllegalArgumentException("vector 必须为长度15的数组");
        }
    }

    private void fillEntity(CbtiPersonalityEntity entity, CbtiPersonalitySaveReq req, String code) throws Exception {
        entity.setCode(code);
        entity.setName(req.getName());
        entity.setMotto(req.getMotto());
        entity.setColor(req.getColor());
        entity.setDescription(req.getDescription());
        entity.setTechStack(req.getTechStack());
        entity.setSpirit(req.getSpirit());
        if (StringUtils.hasText(req.getImageObject())) {
            entity.setImageObject(req.getImageObject());
        }
        entity.setIsSpecial(Boolean.TRUE.equals(req.getIsSpecial()) ? 1 : 0);

        entity.setVector(objectMapper.writeValueAsString(req.getVector()));
        entity.setStrengths(objectMapper.writeValueAsString(Objects.requireNonNullElse(req.getStrengths(), List.of())));
        entity.setWeaknesses(objectMapper.writeValueAsString(Objects.requireNonNullElse(req.getWeaknesses(), List.of())));
    }

    private boolean existsCode(String code, Long ignoreId) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        LambdaQueryWrapper<CbtiPersonalityEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CbtiPersonalityEntity::getIsDeleted, 0);
        wrapper.eq(CbtiPersonalityEntity::getCode, code);
        if (ignoreId != null) {
            wrapper.ne(CbtiPersonalityEntity::getId, ignoreId);
        }
        return cbtiPersonalityMapper.selectCount(wrapper) > 0;
    }

    private CbtiPersonalityEntity getByCode(String code) {
        LambdaQueryWrapper<CbtiPersonalityEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CbtiPersonalityEntity::getIsDeleted, 0);
        wrapper.eq(CbtiPersonalityEntity::getCode, code);
        return cbtiPersonalityMapper.selectOne(wrapper);
    }

    private CbtiPersonalityAdminVO toAdminVO(CbtiPersonalityEntity entity) {
        CbtiPersonalityAdminVO vo = new CbtiPersonalityAdminVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setMotto(entity.getMotto());
        vo.setColor(entity.getColor());
        vo.setDescription(entity.getDescription());
        vo.setTechStack(entity.getTechStack());
        vo.setSpirit(entity.getSpirit());
        vo.setImageObject(entity.getImageObject());
        vo.setIsSpecial(Objects.equals(entity.getIsSpecial(), 1));
        vo.setUpdateTime(entity.getUpdateTime());
        vo.setCreateTime(entity.getCreateTime());

        vo.setVector(readJsonSafely(entity.getVector(), new TypeReference<List<Integer>>() {
        }));
        vo.setStrengths(readJsonSafely(entity.getStrengths(), new TypeReference<List<String>>() {
        }));
        vo.setWeaknesses(readJsonSafely(entity.getWeaknesses(), new TypeReference<List<String>>() {
        }));

        String objectName = entity.getImageObject();
        if (StringUtils.hasText(objectName)) {
            String bucketName = resolveBucketName();
            vo.setImageUrl(buildPreviewUrl(bucketName, objectName));
        }
        return vo;
    }

    private <T> T readJsonSafely(String json, TypeReference<T> typeReference) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveBucketName() {
        if (StringUtils.hasText(cbtiConfig.getBucketName())) {
            return cbtiConfig.getBucketName();
        }
        if (minioConfig != null && StringUtils.hasText(minioConfig.getBucketName())) {
            return minioConfig.getBucketName();
        }
        return "aiolife";
    }

    private String normalizePrefix(String prefix) {
        String p = StringUtils.hasText(prefix) ? prefix : "images/cbti/characters/";
        p = p.startsWith("/") ? p.substring(1) : p;
        return p.endsWith("/") ? p : p + "/";
    }

    private String normalizeCode(String code) {
        return StringUtils.hasText(code) ? code.trim() : null;
    }

    private String buildPreviewUrl(String bucketName, String objectName) {
        String normalized = objectName.startsWith("/") ? objectName.substring(1) : objectName;
        return serveBaseUrl + "/file/preview/" + bucketName + "/" + normalized;
    }

    private String detectExt(String filename, String contentType) {
        if (StringUtils.hasText(filename) && filename.contains(".")) {
            String ext = filename.substring(filename.lastIndexOf('.') + 1).trim().toLowerCase();
            if (StringUtils.hasText(ext)) {
                return ext;
            }
        }
        if (Objects.equals("image/jpeg", contentType)) {
            return "jpg";
        }
        if (Objects.equals("image/png", contentType)) {
            return "png";
        }
        if (Objects.equals("image/gif", contentType)) {
            return "gif";
        }
        if (Objects.equals("image/webp", contentType)) {
            return "webp";
        }
        return "png";
    }
}
