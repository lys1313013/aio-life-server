package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import top.aiolife.core.query.QueryOrder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.aiolife.record.mapper.ICbtiPersonalityMapper;
import top.aiolife.record.mapper.ICbtiResultMapper;
import top.aiolife.record.pojo.entity.CbtiPersonalityEntity;
import top.aiolife.record.pojo.entity.CbtiResultEntity;
import top.aiolife.record.service.ICbtiService;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    private final ICbtiPersonalityMapper cbtiPersonalityMapper;

    private final ICbtiResultMapper cbtiResultMapper;

    private final ObjectMapper objectMapper;

    private volatile JsonNode cachedQuestions;

    private volatile JsonNode cachedDimensions;

    @Override
    public Map<String, Object> getQuestions() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("questions", loadQuestions().get("questions"));
        result.put("hiddenQuestions", loadQuestions().get("hiddenQuestions"));
        result.put("dimensionDefs", loadDimensions().get("dimensionDefs"));
        return result;
    }

    @Override
    public List<CbtiPersonalityEntity> listPersonalities() {
        LambdaQueryWrapper<CbtiPersonalityEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CbtiPersonalityEntity::getIsDeleted, 0);
        wrapper.orderByAsc(CbtiPersonalityEntity::getIsSpecial);
        wrapper.orderByAsc(CbtiPersonalityEntity::getCode);
        return cbtiPersonalityMapper.selectList(wrapper);
    }

    @Override
    public CbtiPersonalityEntity getPersonalityByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        LambdaQueryWrapper<CbtiPersonalityEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CbtiPersonalityEntity::getIsDeleted, 0);
        wrapper.eq(CbtiPersonalityEntity::getCode, code);
        return cbtiPersonalityMapper.selectOne(wrapper);
    }

    @Override
    public Map<String, Object> testAndSave(long userId, Map<Integer, Integer> answers, Map<String, Object> hiddenAnswers) throws Exception {
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
            result.put("personality", special);
            result.put("similarity", 100);
            result.put("dimensions", dimensions);
            result.put("isSpecial", true);
            result.put("matchDetails", List.of());

            saveHistory(userId, special.getCode(), 100, dimensions, answers, hiddenAnswers);
            return result;
        }

        List<CbtiPersonalityEntity> candidates = getNormalPersonalities();
        if (candidates.isEmpty()) {
            candidates = listPersonalities();
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

        result.put("personality", bestPersonality);
        result.put("similarity", bestSimilarity);
        result.put("dimensions", dimensions);
        result.put("isSpecial", false);
        result.put("matchDetails", matchDetails);

        saveHistory(userId, bestPersonality.getCode(), bestSimilarity, dimensions, answers, hiddenAnswers);
        return result;
    }

    @Override
    public List<Map<String, Object>> getUserHistory(long userId) {
        LambdaQueryWrapper<CbtiResultEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CbtiResultEntity::getIsDeleted, 0);
        wrapper.eq(CbtiResultEntity::getUserId, userId);
        QueryOrder.descNullsLast(wrapper, CbtiResultEntity::getCreateTime, CbtiResultEntity::getId);
        List<CbtiResultEntity> list = cbtiResultMapper.selectList(wrapper);

        Map<String, CbtiPersonalityEntity> personalityMap = new HashMap<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (CbtiResultEntity r : list) {
            CbtiPersonalityEntity p = personalityMap.computeIfAbsent(r.getPersonalityCode(), this::getPersonalityByCode);
            Map<String, Object> item = new HashMap<>();
            item.put("id", r.getId());
            item.put("personalityCode", r.getPersonalityCode());
            item.put("similarity", r.getSimilarity());
            item.put("createTime", r.getCreateTime());
            if (p != null) {
                item.put("name", p.getName());
                item.put("motto", p.getMotto());
                item.put("color", p.getColor());
                item.put("imageObject", p.getImageObject());
                item.put("isSpecial", Objects.equals(p.getIsSpecial(), 1));
            }
            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> getHistoryDetail(long id, long userId) {
        CbtiResultEntity entity = cbtiResultMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getUserId(), userId) || !Objects.equals(entity.getIsDeleted(), 0)) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("id", entity.getId());
        result.put("userId", entity.getUserId());
        result.put("personalityCode", entity.getPersonalityCode());
        result.put("similarity", entity.getSimilarity());
        result.put("createTime", entity.getCreateTime());
        result.put("dimensions", safeReadJson(entity.getDimensions()));
        result.put("answers", safeReadJson(entity.getAnswers()));
        result.put("hiddenAnswers", safeReadJson(entity.getHiddenAnswers()));
        CbtiPersonalityEntity p = getPersonalityByCode(entity.getPersonalityCode());
        result.put("personality", p);
        return result;
    }

    @Override
    public boolean deleteHistory(long id, long userId) {
        CbtiResultEntity entity = cbtiResultMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getUserId(), userId) || !Objects.equals(entity.getIsDeleted(), 0)) {
            return false;
        }
        entity.fillUpdateCommonField(userId);
        return cbtiResultMapper.deleteById(entity) > 0;
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
