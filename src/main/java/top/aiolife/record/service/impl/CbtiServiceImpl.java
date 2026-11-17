package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import top.aiolife.config.CbtiConfig;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.exception.ApiException;
import top.aiolife.core.query.QueryOrder;
import top.aiolife.core.util.FileContentPolicy;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.enums.AttachmentBizType;
import top.aiolife.record.mapper.IAttachmentMapper;
import top.aiolife.record.mapper.ICbtiPersonalityMapper;
import top.aiolife.record.mapper.ICbtiResultMapper;
import top.aiolife.record.pojo.entity.AttachmentEntity;
import top.aiolife.record.pojo.entity.CbtiPersonalityEntity;
import top.aiolife.record.pojo.entity.CbtiResultEntity;
import top.aiolife.record.pojo.req.CbtiPersonalitySaveReq;
import top.aiolife.record.pojo.vo.CbtiPersonalityAdminVO;
import top.aiolife.record.service.ICbtiService;
import top.aiolife.record.service.VideoCoverStorage;

/**
 * CBTI 服务实现类
 *
 * @author Ethan
 * @date 2026/04/18
 */
@Service
@RequiredArgsConstructor
public class CbtiServiceImpl implements ICbtiService {

    private static final String QUESTIONS_RESOURCE = "cbti/questions.json";

    private static final String DIMENSIONS_RESOURCE = "cbti/dimensions.json";

    private static final String PERSONALITIES_RESOURCE = "cbti/personalities.json";

    private static final String IMAGE_BIZ_TYPE = AttachmentBizType.CBTI_PERSONALITY.getBizType();

    private final ICbtiPersonalityMapper cbtiPersonalityMapper;

    private final ICbtiResultMapper cbtiResultMapper;

    private final IAttachmentMapper attachmentMapper;

    private final VideoCoverStorage imageStorage;

    private final ObjectMapper objectMapper;

    private final MinioUtil minioUtil;

    private final CbtiConfig cbtiConfig;

    private volatile JsonNode cachedQuestions;

    private volatile JsonNode cachedDimensions;

    @Override
    public Map<String, Object> getQuestions() {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("questions", loadQuestions().get("questions"));
            result.put("hiddenQuestions", loadQuestions().get("hiddenQuestions"));
            result.put("dimensionDefs", loadDimensions().get("dimensionDefs"));
            return result;
        } catch (Exception e) {
            throw new ApiException(ResponseCodeConst.COMMON_FAIL, "读取题库失败: " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> listPersonalityViews() {
        List<CbtiPersonalityEntity> list = listActivePersonalities();
        Map<Long, String> imageUrls = loadImageUrls(list);
        return list.stream().map(p -> toPersonalityView(p, imageUrls.get(p.getId()))).toList();
    }

    @Override
    public Map<String, Object> getPersonalityView(String code) {
        CbtiPersonalityEntity entity = getPersonalityByCode(code);
        if (entity == null) {
            throw new ApiException(ResponseCodeConst.COMMON_FAIL, "人格不存在");
        }
        return toPersonalityView(entity, loadImageUrls(List.of(entity)).get(entity.getId()));
    }

    @Override
    public Map<String, Object> testAndSave(long userId, Map<Integer, Integer> answers, Map<String, Object> hiddenAnswers) {
        try {
            Map<String, Object> result = new HashMap<>();

            List<Map<String, Object>> dimensions = calculateDimensions(answers);
            List<Integer> userVector = dimensions.stream()
                    .map(d -> (Integer) d.get("levelNum"))
                    .toList();

            boolean isSpecial = isCoffeeAddict(hiddenAnswers);
            if (isSpecial) {
                CbtiPersonalityEntity special = getSpecialPersonality();
                if (special == null) {
                    throw new IllegalStateException("未找到隐藏人格数据");
                }
                result.put("personality", personalityView(special));
                result.put("similarity", 100);
                result.put("dimensions", dimensions);
                result.put("isSpecial", true);
                result.put("matchDetails", List.of());

                saveHistory(userId, special.getCode(), 100, dimensions, answers, hiddenAnswers);
                return result;
            }

            List<CbtiPersonalityEntity> candidates = getNormalPersonalities();
            if (candidates.isEmpty()) {
                candidates = listActivePersonalities();
            }
            if (candidates.isEmpty()) {
                throw new IllegalStateException("未找到人格数据");
            }

            List<Map<String, Object>> ranked = new ArrayList<>();
            for (CbtiPersonalityEntity p : candidates) {
                int[] pv = parseVector(p.getVector());
                if (pv.length != 15) {
                    continue;
                }
                int distance = 0;
                int exact = 0;
                for (int i = 0; i < 15; i++) {
                    int diff = Math.abs(userVector.get(i) - pv[i]);
                    distance += diff;
                    if (diff == 0) {
                        exact++;
                    }
                }
                int similarity = Math.max(0, (int) Math.round((1 - distance / 30.0) * 100));
                Map<String, Object> item = new HashMap<>();
                item.put("personality", p);
                item.put("distance", distance);
                item.put("exact", exact);
                item.put("similarity", similarity);
                ranked.add(item);
            }

            ranked.sort((a, b) -> {
                int da = (Integer) a.get("distance");
                int db = (Integer) b.get("distance");
                if (da != db) {
                    return Integer.compare(da, db);
                }
                int ea = (Integer) a.get("exact");
                int eb = (Integer) b.get("exact");
                if (ea != eb) {
                    return Integer.compare(eb, ea);
                }
                int sa = (Integer) a.get("similarity");
                int sb = (Integer) b.get("similarity");
                return Integer.compare(sb, sa);
            });

            Map<String, Object> best = ranked.get(0);
            CbtiPersonalityEntity bestPersonality = (CbtiPersonalityEntity) best.get("personality");
            int bestSimilarity = (Integer) best.get("similarity");

            List<Map<String, Object>> matchDetails = ranked.stream()
                    .limit(5)
                    .map(r -> {
                        CbtiPersonalityEntity rp = (CbtiPersonalityEntity) r.get("personality");
                        Map<String, Object> m = new HashMap<>();
                        m.put("code", rp.getCode());
                        m.put("name", rp.getName());
                        m.put("similarity", r.get("similarity"));
                        return m;
                    })
                    .toList();

            result.put("personality", personalityView(bestPersonality));
            result.put("similarity", bestSimilarity);
            result.put("dimensions", dimensions);
            result.put("isSpecial", false);
            result.put("matchDetails", matchDetails);

            saveHistory(userId, bestPersonality.getCode(), bestSimilarity, dimensions, answers, hiddenAnswers);
            return result;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ResponseCodeConst.COMMON_FAIL, e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> getUserHistoryView(long userId) {
        LambdaQueryWrapper<CbtiResultEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CbtiResultEntity::getIsDeleted, 0);
        wrapper.eq(CbtiResultEntity::getUserId, userId);
        QueryOrder.descNullsLast(wrapper, CbtiResultEntity::getCreateTime, CbtiResultEntity::getId);
        List<CbtiResultEntity> list = cbtiResultMapper.selectList(wrapper);

        Map<String, CbtiPersonalityEntity> personalityMap = new HashMap<>();
        for (CbtiResultEntity r : list) {
            personalityMap.computeIfAbsent(r.getPersonalityCode(), this::getPersonalityByCode);
        }
        Map<Long, String> imageUrls = loadImageUrls(personalityMap.values().stream().filter(Objects::nonNull).toList());

        List<Map<String, Object>> result = new ArrayList<>();
        for (CbtiResultEntity r : list) {
            CbtiPersonalityEntity p = personalityMap.get(r.getPersonalityCode());
            Map<String, Object> item = new HashMap<>();
            item.put("id", r.getId());
            item.put("personalityCode", r.getPersonalityCode());
            item.put("similarity", r.getSimilarity());
            item.put("createTime", r.getCreateTime());
            if (p != null) {
                item.put("name", p.getName());
                item.put("motto", p.getMotto());
                item.put("color", p.getColor());
                item.put("isSpecial", Objects.equals(p.getIsSpecial(), 1));
                String imageUrl = imageUrls.get(p.getId());
                if (StringUtils.hasText(imageUrl)) {
                    item.put("imageUrl", imageUrl);
                }
            }
            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> getHistoryDetailView(long id, long userId) {
        CbtiResultEntity entity = cbtiResultMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getUserId(), userId) || !Objects.equals(entity.getIsDeleted(), 0)) {
            throw new ApiException(ResponseCodeConst.COMMON_FAIL, "记录不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("id", entity.getId());
        result.put("personalityCode", entity.getPersonalityCode());
        result.put("similarity", entity.getSimilarity());
        result.put("createTime", entity.getCreateTime());
        result.put("dimensions", safeReadJson(entity.getDimensions()));
        result.put("answers", safeReadJson(entity.getAnswers()));
        result.put("hiddenAnswers", safeReadJson(entity.getHiddenAnswers()));
        CbtiPersonalityEntity p = getPersonalityByCode(entity.getPersonalityCode());
        result.put("personality", p == null ? null : personalityView(p));
        return result;
    }

    @Override
    public void deleteHistoryForUser(long id, long userId) {
        CbtiResultEntity entity = cbtiResultMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getUserId(), userId) || !Objects.equals(entity.getIsDeleted(), 0)) {
            throw new ApiException(ResponseCodeConst.COMMON_FAIL, "记录不存在");
        }
        entity.fillUpdateCommonField(userId);
        cbtiResultMapper.deleteById(entity);
    }

    @Override
    public List<CbtiPersonalityAdminVO> listPersonalitiesForAdmin() {
        List<CbtiPersonalityEntity> list = listActivePersonalities();
        Map<Long, String> imageUrls = loadImageUrls(list);
        return list.stream().map(p -> toAdminVO(p, imageUrls.get(p.getId()))).toList();
    }

    @Override
    public CbtiPersonalityAdminVO createPersonality(long userId, CbtiPersonalitySaveReq req) {
        validateSaveReqForCreate(req);

        String code = normalizeCode(req.getCode());
        if (existsCode(code, null)) {
            throw new ApiException(ResponseCodeConst.PARAM_FAIL, "code 已存在");
        }

        CbtiPersonalityEntity entity = new CbtiPersonalityEntity();
        fillEntity(entity, req, code, "新增失败: ");
        entity.fillCreateCommonField(userId);
        cbtiPersonalityMapper.insert(entity);
        return toAdminVO(entity, null);
    }

    @Override
    public CbtiPersonalityAdminVO updatePersonality(long userId, long id, CbtiPersonalitySaveReq req) {
        validateSaveReqForUpdate(req);

        CbtiPersonalityEntity exist = cbtiPersonalityMapper.selectById(id);
        if (exist == null || !Objects.equals(exist.getIsDeleted(), 0)) {
            throw new ApiException(ResponseCodeConst.PARAM_FAIL, "记录不存在");
        }

        String code = normalizeCode(req.getCode());
        if (StringUtils.hasText(code) && existsCode(code, id)) {
            throw new ApiException(ResponseCodeConst.PARAM_FAIL, "code 已存在");
        }

        fillEntity(exist, req, StringUtils.hasText(code) ? code : exist.getCode(), "更新失败: ");
        exist.fillUpdateCommonField(userId);
        cbtiPersonalityMapper.updateById(exist);
        return toAdminVO(exist, loadImageUrls(List.of(exist)).get(exist.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePersonality(long userId, long id) {
        CbtiPersonalityEntity exist = cbtiPersonalityMapper.selectById(id);
        if (exist == null || !Objects.equals(exist.getIsDeleted(), 0)) {
            return;
        }
        exist.setIsDeleted(1);
        exist.fillUpdateCommonField(userId);
        cbtiPersonalityMapper.updateById(exist);
        attachmentMapper.update(null, new LambdaUpdateWrapper<AttachmentEntity>()
                .eq(AttachmentEntity::getBizType, IMAGE_BIZ_TYPE)
                .eq(AttachmentEntity::getBizId, id)
                .eq(AttachmentEntity::getIsDeleted, 0)
                .set(AttachmentEntity::getIsDeleted, 1)
                .set(AttachmentEntity::getIsPublic, 0)
                .set(AttachmentEntity::getUpdateUser, userId)
                .set(AttachmentEntity::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> uploadPersonalityImage(long userId, String code, MultipartFile file) {
        if (!StringUtils.hasText(code)) {
            throw new ApiException(ResponseCodeConst.PARAM_FAIL, "code 不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(ResponseCodeConst.PARAM_FAIL, "文件不能为空");
        }
        if (file.getSize() > FileContentPolicy.MAX_IMAGE_BYTES)
            throw new ApiException(ResponseCodeConst.PARAM_FAIL, "图片不能超过10MB");

        CbtiPersonalityEntity exist = getPersonalityByCode(normalizeCode(code));
        if (exist == null) {
            throw new ApiException(ResponseCodeConst.PARAM_FAIL, "人格不存在");
        }

        byte[] bytes;
        try (var input = file.getInputStream()) {
            bytes = input.readNBytes(FileContentPolicy.MAX_IMAGE_BYTES + 1);
        } catch (IOException e) {
            throw new ApiException(ResponseCodeConst.PARAM_FAIL, "图片无法读取");
        }
        var image = FileContentPolicy.requireImage(bytes);
        Map<String, Object> data = new HashMap<>();
        data.put("imageUrl", replaceImage(exist.getId(), userId, bytes, image));
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initImages() {
        if (!cbtiConfig.isEnabled()) {
            throw new ApiException(ResponseCodeConst.COMMON_FAIL, "CBTI 未启用");
        }
        Map<String, String> files = readPersonalityImages();
        List<CbtiPersonalityEntity> list = listActivePersonalities();
        if (list.isEmpty()) {
            return;
        }
        List<Long> ids = list.stream().map(CbtiPersonalityEntity::getId).toList();
        // 已有人工上传的图片不覆盖，初始化只补齐没有图片的人格。
        var bound = attachmentMapper.selectWithObjectByBiz(IMAGE_BIZ_TYPE, ids).stream()
                .map(AttachmentEntity::getBizId).collect(java.util.stream.Collectors.toSet());

        for (CbtiPersonalityEntity entity : list) {
            if (bound.contains(entity.getId())) {
                continue;
            }
            String fileName = files.get(entity.getCode());
            if (!StringUtils.hasText(fileName)) {
                continue;
            }
            byte[] bytes = readCharacterImage(fileName);
            if (bytes == null) {
                continue;
            }
            replaceImage(entity.getId(), null, bytes, FileContentPolicy.requireImage(bytes));
        }
    }

    /** 优先读取配置目录，未配置或缺失时回退到打包在 classpath 的内置图片。 */
    private byte[] readCharacterImage(String fileName) {
        String configured = cbtiConfig.getCharactersDir();
        try {
            if (StringUtils.hasText(configured)) {
                Path path = Path.of(configured).resolve(fileName);
                if (Files.isRegularFile(path)) {
                    return Files.readAllBytes(path);
                }
            }
            ClassPathResource resource = new ClassPathResource("cbti/characters/" + fileName);
            if (!resource.exists()) {
                return null;
            }
            try (InputStream input = resource.getInputStream()) {
                return input.readNBytes(FileContentPolicy.MAX_IMAGE_BYTES + 1);
            }
        } catch (IOException e) {
            throw new ApiException(ResponseCodeConst.COMMON_FAIL, "读取人格图片失败: " + fileName);
        }
    }

    /** 内容寻址存储字节后替换人格图片附件；同一人格只保留一条有效附件。 */
    private String replaceImage(long personalityId, Long operator, byte[] bytes, FileContentPolicy.ImageType image) {
        if (cbtiPersonalityMapper.lockById(personalityId) == null) {
            throw new ApiException(ResponseCodeConst.PARAM_FAIL, "人格不存在");
        }
        var object = imageStorage.store(bytes, operator, image);
        List<AttachmentEntity> current = attachmentMapper.selectWithObjectByBiz(IMAGE_BIZ_TYPE, List.of(personalityId));
        String imageUrl = minioUtil.getPublicImageUrl(object.getContentHash(), object.getContentType());
        if (current.size() == 1 && Objects.equals(current.getFirst().getStorageObjectId(), object.getId())) {
            return imageUrl;
        }
        attachmentMapper.update(null, new LambdaUpdateWrapper<AttachmentEntity>()
                .eq(AttachmentEntity::getBizType, IMAGE_BIZ_TYPE)
                .eq(AttachmentEntity::getBizId, personalityId)
                .eq(AttachmentEntity::getIsDeleted, 0)
                .set(AttachmentEntity::getIsDeleted, 1)
                .set(AttachmentEntity::getIsPublic, 0)
                .set(AttachmentEntity::getUpdateUser, operator)
                .set(AttachmentEntity::getUpdateTime, LocalDateTime.now()));
        AttachmentEntity attachment = new AttachmentEntity();
        attachment.fillCreateCommonField(operator);
        // 人格图片是系统公共资源，不归属具体用户，注销账号也不会删除共享图片。
        attachment.setUserId(null);
        attachment.setStorageObjectId(object.getId());
        attachment.setBizType(IMAGE_BIZ_TYPE);
        attachment.setBizId(personalityId);
        attachment.setIsPublic(1);
        attachmentMapper.insert(attachment);
        return imageUrl;
    }

    private void validateSaveReqForCreate(CbtiPersonalitySaveReq req) {
        try {
            validateSaveReq(req, false);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ResponseCodeConst.COMMON_FAIL, "新增失败: " + e.getMessage());
        }
    }

    private void validateSaveReqForUpdate(CbtiPersonalitySaveReq req) {
        try {
            validateSaveReq(req, true);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ResponseCodeConst.COMMON_FAIL, "更新失败: " + e.getMessage());
        }
    }

    private void validateSaveReq(CbtiPersonalitySaveReq req, boolean isUpdate) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (!isUpdate && !StringUtils.hasText(req.getCode())) {
            throw new IllegalArgumentException("code 不能为空");
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

    private void fillEntity(CbtiPersonalityEntity entity, CbtiPersonalitySaveReq req, String code, String errorPrefix) {
        entity.setCode(code);
        entity.setName(req.getName());
        entity.setMotto(req.getMotto());
        entity.setColor(req.getColor());
        entity.setDescription(req.getDescription());
        entity.setTechStack(req.getTechStack());
        entity.setSpirit(req.getSpirit());
        entity.setIsSpecial(Boolean.TRUE.equals(req.getIsSpecial()) ? 1 : 0);

        try {
            entity.setVector(objectMapper.writeValueAsString(req.getVector()));
            entity.setStrengths(objectMapper.writeValueAsString(Objects.requireNonNullElse(req.getStrengths(), List.of())));
            entity.setWeaknesses(objectMapper.writeValueAsString(Objects.requireNonNullElse(req.getWeaknesses(), List.of())));
        } catch (JsonProcessingException e) {
            throw new ApiException(ResponseCodeConst.COMMON_FAIL, errorPrefix + e.getMessage());
        }
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

    private CbtiPersonalityEntity getPersonalityByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        LambdaQueryWrapper<CbtiPersonalityEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CbtiPersonalityEntity::getIsDeleted, 0);
        wrapper.eq(CbtiPersonalityEntity::getCode, code);
        return cbtiPersonalityMapper.selectOne(wrapper);
    }

    private List<CbtiPersonalityEntity> listActivePersonalities() {
        LambdaQueryWrapper<CbtiPersonalityEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CbtiPersonalityEntity::getIsDeleted, 0);
        wrapper.orderByAsc(CbtiPersonalityEntity::getIsSpecial);
        wrapper.orderByAsc(CbtiPersonalityEntity::getCode);
        return cbtiPersonalityMapper.selectList(wrapper);
    }

    private Map<String, Object> personalityView(CbtiPersonalityEntity entity) {
        return toPersonalityView(entity, loadImageUrls(List.of(entity)).get(entity.getId()));
    }

    private Map<String, Object> toPersonalityView(CbtiPersonalityEntity entity, String imageUrl) {
        Map<String, Object> p = new HashMap<>();
        p.put("code", entity.getCode());
        p.put("name", entity.getName());
        p.put("motto", entity.getMotto());
        p.put("color", entity.getColor());
        p.put("description", entity.getDescription());
        p.put("techStack", entity.getTechStack());
        p.put("spirit", entity.getSpirit());
        p.put("isSpecial", Objects.equals(entity.getIsSpecial(), 1));
        if (StringUtils.hasText(imageUrl)) {
            p.put("imageUrl", imageUrl);
        }
        p.put("vector", readJsonValue(entity.getVector()));
        p.put("strengths", readJsonValue(entity.getStrengths()));
        p.put("weaknesses", readJsonValue(entity.getWeaknesses()));
        return p;
    }

    /** 批量读取人格图片的公开内容地址，避免列表逐个查询附件。 */
    private Map<Long, String> loadImageUrls(Collection<CbtiPersonalityEntity> personalities) {
        List<Long> ids = personalities.stream().map(CbtiPersonalityEntity::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> urls = new HashMap<>();
        for (AttachmentEntity attachment : attachmentMapper.selectWithObjectByBiz(IMAGE_BIZ_TYPE, ids)) {
            String url = minioUtil.getPublicImageUrl(attachment.getHashValue(), attachment.getFileType());
            if (attachment.getBizId() != null && StringUtils.hasText(url)) {
                urls.put(attachment.getBizId(), url);
            }
        }
        return urls;
    }

    private CbtiPersonalityAdminVO toAdminVO(CbtiPersonalityEntity entity, String imageUrl) {
        CbtiPersonalityAdminVO vo = new CbtiPersonalityAdminVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setMotto(entity.getMotto());
        vo.setColor(entity.getColor());
        vo.setDescription(entity.getDescription());
        vo.setTechStack(entity.getTechStack());
        vo.setSpirit(entity.getSpirit());
        vo.setIsSpecial(Objects.equals(entity.getIsSpecial(), 1));
        vo.setUpdateTime(entity.getUpdateTime());
        vo.setCreateTime(entity.getCreateTime());

        vo.setVector(readJsonSafely(entity.getVector(), new TypeReference<List<Integer>>() {
        }));
        vo.setStrengths(readJsonSafely(entity.getStrengths(), new TypeReference<List<String>>() {
        }));
        vo.setWeaknesses(readJsonSafely(entity.getWeaknesses(), new TypeReference<List<String>>() {
        }));

        if (StringUtils.hasText(imageUrl)) {
            vo.setImageUrl(imageUrl);
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

    private Object readJsonValue(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    private String normalizeCode(String code) {
        return StringUtils.hasText(code) ? code.trim() : null;
    }

    /** 内置人格图片清单，源文件按人格代码映射，初始化只补缺不覆盖人工上传。 */
    private Map<String, String> readPersonalityImages() {
        try {
            Map<String, String> files = new HashMap<>();
            for (JsonNode item : readJsonResource(PERSONALITIES_RESOURCE)) {
                JsonNode code = item.get("code");
                JsonNode image = item.get("image");
                if (code != null && image != null
                        && StringUtils.hasText(code.asText()) && StringUtils.hasText(image.asText())) {
                    files.put(code.asText(), image.asText());
                }
            }
            return files;
        } catch (Exception e) {
            throw new ApiException(ResponseCodeConst.COMMON_FAIL, "读取 CBTI 人格图片清单失败: " + e.getMessage());
        }
    }

    private boolean isCoffeeAddict(Map<String, Object> hiddenAnswers) {
        if (hiddenAnswers == null) {
            return false;
        }
        Object drink = hiddenAnswers.get("drink");
        Object drinkAttitude = hiddenAnswers.get("drinkAttitude");
        return Objects.equals("coffee", drink) && Objects.equals("addict", drinkAttitude);
    }

    private void saveHistory(long userId,
                             String personalityCode,
                             int similarity,
                             List<Map<String, Object>> dimensions,
                             Map<Integer, Integer> answers,
                             Map<String, Object> hiddenAnswers) throws Exception {
        CbtiResultEntity entity = new CbtiResultEntity();
        entity.setUserId(userId);
        entity.setPersonalityCode(personalityCode);
        entity.setSimilarity(similarity);
        entity.setDimensions(objectMapper.writeValueAsString(dimensions));
        entity.setAnswers(objectMapper.writeValueAsString(Objects.requireNonNullElse(answers, Map.of())));
        entity.setHiddenAnswers(objectMapper.writeValueAsString(Objects.requireNonNullElse(hiddenAnswers, Map.of())));
        entity.fillCreateCommonField(userId);
        cbtiResultMapper.insert(entity);
    }

    private List<Map<String, Object>> calculateDimensions(Map<Integer, Integer> answers) throws Exception {
        JsonNode qRoot = loadQuestions();
        JsonNode dRoot = loadDimensions();

        List<Map<String, Object>> dimensionDefs = objectMapper.convertValue(
                dRoot.get("dimensionDefs"),
                new TypeReference<List<Map<String, Object>>>() {
                }
        );

        List<Map<String, Object>> questions = objectMapper.convertValue(
                qRoot.get("questions"),
                new TypeReference<List<Map<String, Object>>>() {
                }
        );

        Map<String, List<Map<String, Object>>> byDim = new HashMap<>();
        for (Map<String, Object> q : questions) {
            Object dim = q.get("dimension");
            if (dim == null) {
                continue;
            }
            byDim.computeIfAbsent(String.valueOf(dim), k -> new ArrayList<>()).add(q);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> def : dimensionDefs) {
            String code = String.valueOf(def.get("code"));
            List<Map<String, Object>> dimQuestions = byDim.getOrDefault(code, List.of());
            int max = dimQuestions.size() * 3;
            int raw = 0;
            for (Map<String, Object> q : dimQuestions) {
                int id = ((Number) q.get("id")).intValue();
                raw += answers != null && answers.get(id) != null ? answers.get(id) : 2;
            }
            int percentage = max <= 0 ? 0 : (int) Math.round(raw * 100.0 / max);
            String level = rawToLevel(raw);
            int levelNum = levelToNum(level);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", code);
            item.put("name", def.get("name"));
            item.put("model", def.get("model"));
            item.put("modelName", def.get("modelName"));
            item.put("raw", raw);
            item.put("max", max);
            item.put("level", level);
            item.put("levelNum", levelNum);
            item.put("percentage", percentage);
            Object levels = def.get("levels");
            if (levels instanceof Map<?, ?> m) {
                item.put("levelDesc", m.get(level));
            }
            result.add(item);
        }
        return result;
    }

    private String rawToLevel(int raw) {
        if (raw <= 3) {
            return "L";
        }
        if (raw == 4) {
            return "M";
        }
        return "H";
    }

    private int levelToNum(String level) {
        if (Objects.equals(level, "L")) {
            return 0;
        }
        if (Objects.equals(level, "M")) {
            return 1;
        }
        return 2;
    }

    private int[] parseVector(String vectorJson) {
        if (!StringUtils.hasText(vectorJson)) {
            return new int[0];
        }
        try {
            List<Integer> list = objectMapper.readValue(vectorJson, new TypeReference<List<Integer>>() {
            });
            int[] arr = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                arr[i] = list.get(i);
            }
            return arr;
        } catch (Exception e) {
            return new int[0];
        }
    }

    private CbtiPersonalityEntity getSpecialPersonality() {
        LambdaQueryWrapper<CbtiPersonalityEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CbtiPersonalityEntity::getIsDeleted, 0);
        wrapper.eq(CbtiPersonalityEntity::getIsSpecial, 1);
        wrapper.orderByAsc(CbtiPersonalityEntity::getCode);
        return cbtiPersonalityMapper.selectOne(wrapper);
    }

    private List<CbtiPersonalityEntity> getNormalPersonalities() {
        LambdaQueryWrapper<CbtiPersonalityEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CbtiPersonalityEntity::getIsDeleted, 0);
        wrapper.eq(CbtiPersonalityEntity::getIsSpecial, 0);
        wrapper.orderByAsc(CbtiPersonalityEntity::getCode);
        return cbtiPersonalityMapper.selectList(wrapper);
    }

    private Object safeReadJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    private JsonNode loadQuestions() throws Exception {
        JsonNode local = cachedQuestions;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedQuestions != null) {
                return cachedQuestions;
            }
            cachedQuestions = readJsonResource(QUESTIONS_RESOURCE);
            return cachedQuestions;
        }
    }

    private JsonNode loadDimensions() throws Exception {
        JsonNode local = cachedDimensions;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedDimensions != null) {
                return cachedDimensions;
            }
            cachedDimensions = readJsonResource(DIMENSIONS_RESOURCE);
            return cachedDimensions;
        }
    }

    private JsonNode readJsonResource(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readTree(inputStream);
        }
    }
}
