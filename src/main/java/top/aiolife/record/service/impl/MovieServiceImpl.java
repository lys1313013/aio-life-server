package top.aiolife.record.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import top.aiolife.record.mapper.IMovieMapper;
import top.aiolife.record.pojo.entity.MovieEntity;
import top.aiolife.record.pojo.query.MovieQuery;
import top.aiolife.record.pojo.req.MovieReq;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;
import top.aiolife.record.pojo.vo.MovieVO;
import top.aiolife.record.service.IMovieService;
import top.aiolife.record.service.IFileService;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.util.DoubanSubjectUrl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieServiceImpl extends ServiceImpl<IMovieMapper, MovieEntity> implements IMovieService {

    private final IFileService fileService;

    @Override
    public Page<MovieVO> pageList(MovieQuery query) {
        Long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<MovieEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MovieEntity::getUserId, userId);
        
        if (query.getType() != null) {
            wrapper.eq(MovieEntity::getType, query.getType());
        }
        if (query.getStatuses() != null && !query.getStatuses().isEmpty()) {
            wrapper.in(MovieEntity::getStatus, query.getStatuses());
        } else if (query.getStatus() != null) {
            wrapper.eq(MovieEntity::getStatus, query.getStatus());
        } else if (Boolean.TRUE.equals(query.getActiveOnly())) {
            wrapper.in(MovieEntity::getStatus,
                    ProgressStatusEnum.NOT_STARTED, ProgressStatusEnum.IN_PROGRESS);
        }
        if (StrUtil.isNotBlank(query.getTitle())) {
            wrapper.and(condition -> condition
                    .like(MovieEntity::getTitle, query.getTitle())
                    .or()
                    .like(MovieEntity::getDirector, query.getTitle()));
        }
        if (StrUtil.isNotBlank(query.getDirector())) {
            wrapper.like(MovieEntity::getDirector, query.getDirector());
        }
        wrapper.last("ORDER BY FIELD(status, 'not_started', 'in_progress', 'completed', 'on_hold'), "
                + "finish_time DESC, create_time DESC");

        Page<MovieEntity> page = new Page<>(query.getCurrent() == null ? 1 : query.getCurrent(), query.getSize() == null ? 10 : query.getSize());
        Page<MovieEntity> entityPage = this.page(page, wrapper);

        Page<MovieVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());

        List<MovieVO> voList = entityPage.getRecords().stream().map(entity -> {
            MovieVO vo = new MovieVO();
            BeanUtil.copyProperties(entity, vo);
            vo.setId(String.valueOf(entity.getId()));
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    public Long saveRecord(MovieReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        ensureCoverUploaded(req, true);
        MovieEntity entity = new MovieEntity();
        BeanUtil.copyProperties(req, entity);
        entity.setUserId(userId);
        entity.fillCreateCommonField(userId);

        if (entity.getStatus() == ProgressStatusEnum.IN_PROGRESS && entity.getStartTime() == null) {
            entity.setStartTime(LocalDateTime.now());
        }
        if (entity.getStatus() == ProgressStatusEnum.COMPLETED && entity.getFinishTime() == null) {
            entity.setFinishTime(LocalDateTime.now());
        }

        this.save(entity);
        return entity.getId();
    }

    @Override
    public void updateRecord(MovieReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        ensureCoverUploaded(req, true);
        MovieEntity entity = this.getById(req.getId());
        if (entity == null || !entity.getUserId().equals(userId)) {
            throw new RuntimeException("记录不存在或无权限");
        }

        BeanUtil.copyProperties(req, entity);
        entity.fillUpdateCommonField(userId);

        if (entity.getStatus() == ProgressStatusEnum.IN_PROGRESS && entity.getStartTime() == null) {
            entity.setStartTime(LocalDateTime.now());
        }
        if (entity.getStatus() == ProgressStatusEnum.COMPLETED && entity.getFinishTime() == null) {
            entity.setFinishTime(LocalDateTime.now());
        }

        this.updateById(entity);
    }

    @Override
    public void deleteRecord(Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        MovieEntity entity = this.getById(id);
        if (entity != null && entity.getUserId().equals(userId)) {
            this.removeById(id);
        }
    }

    @Override
    public MovieReq parseDouban(String url) {
        DoubanSubjectUrl.Subject subject = DoubanSubjectUrl.parse(url);
        if (subject.mediaType() != DoubanSubjectUrl.MediaType.MOVIE) {
            throw new IllegalArgumentException("该地址不是豆瓣电影条目地址");
        }
        url = subject.canonicalUrl();
        MovieReq res = new MovieReq();
        res.setUrl(url);
        res.setType(1); // 默认为电影

        try {
            // 提取豆瓣 ID
            String doubanId = cn.hutool.core.util.ReUtil.get("subject/(\\d+)", url, 1);
            if (StrUtil.isNotBlank(doubanId)) {
                // 优先尝试使用豆瓣 Rexxar API 获取结构化数据 (先当成电影请求)
                boolean apiSuccess = parseFromRexxarApi(doubanId, "movie", res);
                if (!apiSuccess) {
                    // 如果 404，说明可能是电视剧，尝试 tv 接口
                    apiSuccess = parseFromRexxarApi(doubanId, "tv", res);
                }
                if (apiSuccess && StrUtil.isNotBlank(res.getTitle())) {
                    log.info("Douban Rexxar API parse success: {}", res.getTitle());
                    ensureCoverUploaded(res, false);
                    return res;
                }
            }

            // API 失败则降级使用移动端 HTML 解析
            String bid = cn.hutool.core.util.RandomUtil.randomString(11);
            String mobileUrl = url.replace("movie.douban.com", "m.douban.com/movie");

            Document doc = Jsoup.connect(mobileUrl)
                    .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Cache-Control", "no-cache")
                    .header("Connection", "keep-alive")
                    .header("Referer", "https://m.douban.com/")
                    .header("Cookie", "bid=" + bid + ";")
                    .timeout(10000)
                    .get();

            log.info("Douban parse HTML title: {}", doc.title());
            
            // 1. 尝试从 JSON-LD 提取信息
            parseFromJsonLd(doc, res);

            // 2. 如果 JSON-LD 未获取到关键信息，尝试从 HTML 元素获取
            parseFromHtml(doc, res);

            // 3. 校验解析结果，为空则提示异常
            if (StrUtil.isBlank(res.getTitle())) {
                 throw new RuntimeException("豆瓣反爬限制或页面结构改变，解析失败，请手动填写");
            }

            // 4. 下载封面图并上传到 MinIO，解决豆瓣 CDN 防盗链问题
            ensureCoverUploaded(res, false);

        } catch (Exception e) {
            log.error("解析豆瓣链接失败: {}", url, e);
            throw new RuntimeException("解析豆瓣链接失败，可能触发反爬限制，请稍后再试或手动填写");
        }
        
        return res;
    }

    @Override
    public MovieEntity findByDoubanSubjectId(String subjectId) {
        if (StrUtil.isBlank(subjectId) || !subjectId.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("豆瓣 subjectId 格式不正确");
        }
        Long userId = StpUtil.getLoginIdAsLong();
        return this.lambdaQuery()
                .eq(MovieEntity::getUserId, userId)
                .like(MovieEntity::getUrl, "/subject/" + subjectId)
                .orderByAsc(MovieEntity::getId)
                .list()
                .stream()
                .filter(entity -> DoubanSubjectUrl.matches(
                        entity.getUrl(), DoubanSubjectUrl.MediaType.MOVIE, subjectId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void updateCoverFileId(Long id, String fileId) {
        if (id == null || StrUtil.isBlank(fileId)) {
            throw new IllegalArgumentException("记录ID和封面文件ID不能为空");
        }
        Long userId = StpUtil.getLoginIdAsLong();
        boolean updated = this.lambdaUpdate()
                .eq(MovieEntity::getId, id)
                .eq(MovieEntity::getUserId, userId)
                .set(MovieEntity::getFileId, fileId)
                .set(MovieEntity::getUpdateUser, userId)
                .set(MovieEntity::getUpdateTime, LocalDateTime.now())
                .update();
        if (!updated) {
            throw new IllegalArgumentException("观影记录不存在或无权限");
        }
    }

    /**
     * 尝试从豆瓣 Rexxar API 获取结构化数据
     */
    private boolean parseFromRexxarApi(String id, String type, MovieReq res) {
        try {
            String apiUrl = "https://m.douban.com/rexxar/api/v2/" + type + "/" + id;
            String jsonStr = Jsoup.connect(apiUrl)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                    .header("Referer", "https://m.douban.com/movie/subject/" + id + "/")
                    .timeout(5000)
                    .execute()
                    .body();

            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonStr);
            if (root.has("code") && root.get("code").asInt() == 404) {
                return false;
            }

            applyRexxarData(root, res);
            return true;
        } catch (Exception e) {
            log.warn("Douban Rexxar API parse failed for {}/{}: {}", type, id, e.getMessage());
            return false;
        }
    }

    /**
     * 兼容豆瓣 Rexxar 不同版本的影视字段结构。
     */
    void applyRexxarData(com.fasterxml.jackson.databind.JsonNode root, MovieReq res) {
        if (root.hasNonNull("title")) {
            res.setTitle(root.get("title").asText());
        }

        com.fasterxml.jackson.databind.JsonNode pic = root.path("pic");
        com.fasterxml.jackson.databind.JsonNode cover = root.path("cover");
        String coverUrl = firstText(
                pic.path("large"),
                pic.path("normal"),
                cover.path("image").path("large").path("url"),
                cover.path("image").path("normal").path("url"),
                root.path("cover_url"),
                cover.path("url"));
        if (StrUtil.isNotBlank(coverUrl)) {
            res.setCoverImgUrl(coverUrl);
        }

        if (root.has("directors") && root.get("directors").isArray() && root.get("directors").size() > 0) {
            res.setDirector(root.get("directors").get(0).get("name").asText());
        }

        // 时长或集数
        if (root.has("episodes_count") && root.get("episodes_count").asInt() > 0) {
            res.setTotalProgress(root.get("episodes_count").asInt());
            res.setType(2); // 电视剧
        } else if (root.has("durations") && root.get("durations").isArray() && root.get("durations").size() > 0) {
            String durationStr = root.get("durations").get(0).asText();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(durationStr);
            if (m.find()) {
                res.setTotalProgress(Integer.parseInt(m.group(1)));
            }
        }
    }

    private String firstText(com.fasterxml.jackson.databind.JsonNode... nodes) {
        for (com.fasterxml.jackson.databind.JsonNode node : nodes) {
            if (node != null && node.isTextual() && StrUtil.isNotBlank(node.asText())) {
                return node.asText();
            }
        }
        return null;
    }

    void ensureCoverUploaded(MovieReq res, boolean required) {
        if (StrUtil.isBlank(res.getCoverImgUrl()) || StrUtil.isNotBlank(res.getFileId())) {
            return;
        }
        try {
            var fileVO = fileService.uploadFromUrl(res.getCoverImgUrl(), top.aiolife.record.enums.FileBizType.MOVIE);
            res.setFileId(fileVO.getId());
            log.info("封面图已上传至 MinIO: fileId={}", fileVO.getId());
        } catch (Exception e) {
            if (required) {
                throw new IllegalStateException("封面图上传失败，请确认 MinIO 服务可用后重试", e);
            }
            log.warn("封面图上传 MinIO 失败，解析结果暂时保留原始 URL: {}", res.getCoverImgUrl(), e);
        }
    }

    /**
     * 从 JSON-LD 数据中解析影视信息
     */
    private void parseFromJsonLd(Document doc, MovieReq res) {
        Element jsonLd = doc.selectFirst("script[type=application/ld+json]");
        if (jsonLd == null) {
            return;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode rootNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonLd.data());
            
            if (rootNode.has("name") && StrUtil.isBlank(res.getTitle())) {
                res.setTitle(rootNode.get("name").asText());
            }
            if (rootNode.has("image") && StrUtil.isBlank(res.getCoverImgUrl())) {
                String imgUrl = rootNode.get("image").asText();
                res.setCoverImgUrl(imgUrl.replace("s/public", "l/public").replace("s/pic", "l/pic"));
            }
            if (rootNode.has("director")) {
                com.fasterxml.jackson.databind.JsonNode directors = rootNode.get("director");
                if (directors.isArray() && !directors.isEmpty() && StrUtil.isBlank(res.getDirector())) {
                    res.setDirector(directors.get(0).get("name").asText());
                }
            }
        } catch (Exception ex) {
            log.warn("解析 JSON-LD 失败", ex);
        }
    }

    /**
     * 从 HTML 标签中解析影视信息
     */
    private void parseFromHtml(Document doc, MovieReq res) {
        // 获取标题
        if (StrUtil.isBlank(res.getTitle())) {
            Element titleElement = doc.selectFirst("meta[property=og:title]");
            if (titleElement != null) {
                res.setTitle(titleElement.attr("content"));
            } else {
                Element h1 = doc.selectFirst("h1 span");
                if (h1 != null) {
                    res.setTitle(h1.text());
                } else {
                    // 兜底获取 title 标签
                    Element titleFallback = doc.selectFirst("title");
                    if (titleFallback != null) {
                        String titleText = titleFallback.text().replace("(豆瓣)", "").trim();
                        if (!titleText.contains("302 Found") && !titleText.contains("豆瓣") && !titleText.isEmpty()) {
                            res.setTitle(titleText);
                        }
                    }
                }
            }
        }

        // 获取封面
        if (StrUtil.isBlank(res.getCoverImgUrl())) {
            Element imageElement = doc.selectFirst("meta[property=og:image]");
            String coverUrl = imageElement != null ? imageElement.attr("content") : null;
            if (StrUtil.isBlank(coverUrl)) {
                Element img = doc.selectFirst("#mainpic a img");
                coverUrl = img != null ? img.attr("src") : null;
            }
            if (StrUtil.isNotBlank(coverUrl)) {
                res.setCoverImgUrl(coverUrl.replace("s/public", "l/public").replace("s/pic", "l/pic"));
            }
        }

        // 获取导演
        if (StrUtil.isBlank(res.getDirector())) {
            Element directorElement = doc.selectFirst("meta[property=video:director]");
            if (directorElement != null) {
                res.setDirector(directorElement.attr("content"));
            } else {
                Element directorSpan = doc.selectFirst("#info span.attrs a");
                if (directorSpan != null) {
                    res.setDirector(directorSpan.text());
                }
            }
        }

        // 获取总集数/时长
        if (res.getTotalProgress() == null || res.getTotalProgress() == 0) {
            Element runtimeSpan = doc.selectFirst("span[property=v:runtime]");
            if (runtimeSpan != null) {
                try {
                    res.setTotalProgress(Integer.parseInt(runtimeSpan.attr("content")));
                } catch (NumberFormatException ignored) {}
            } else {
                Element episodesSpan = doc.selectFirst("#info span.pl:contains(集数)");
                if (episodesSpan != null && episodesSpan.nextSibling() != null) {
                    try {
                        res.setTotalProgress(Integer.parseInt(episodesSpan.nextSibling().toString().trim()));
                    } catch (NumberFormatException ignored) {}
                } else {
                    // 移动端兜底获取时长
                    Element subMeta = doc.selectFirst(".sub-meta");
                    if (subMeta != null) {
                        String metaText = subMeta.text();
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("片长(\\d+)分钟").matcher(metaText);
                        if (m.find()) {
                            res.setTotalProgress(Integer.parseInt(m.group(1)));
                        } else {
                            // 匹配集数
                            m = java.util.regex.Pattern.compile("(\\d+)集").matcher(metaText);
                            if (m.find()) {
                                res.setTotalProgress(Integer.parseInt(m.group(1)));
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public List<MovieVO> listActive() {
        Long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<MovieEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MovieEntity::getUserId, userId);
        wrapper.in(MovieEntity::getStatus,
                ProgressStatusEnum.NOT_STARTED, ProgressStatusEnum.IN_PROGRESS); // 想看, 在看
        wrapper.orderByDesc(MovieEntity::getUpdateTime);
        
        List<MovieEntity> entities = this.list(wrapper);
        return entities.stream().map(entity -> {
            MovieVO vo = new MovieVO();
            BeanUtil.copyProperties(entity, vo);
            vo.setId(String.valueOf(entity.getId()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public MovieVO getVOById(Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        MovieEntity entity = this.lambdaQuery()
                .eq(MovieEntity::getId, id)
                .eq(MovieEntity::getUserId, userId)
                .one();
        if (entity == null) return null;
        MovieVO vo = new MovieVO();
        BeanUtil.copyProperties(entity, vo);
        vo.setId(String.valueOf(entity.getId()));
        return vo;
    }
}
