package top.aiolife.record.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.aiolife.record.mapper.ReadRecordMapper;
import top.aiolife.record.pojo.entity.ReadRecordEntity;
import top.aiolife.record.pojo.query.ReadRecordQuery;
import top.aiolife.record.pojo.req.ReadRecordReq;
import top.aiolife.record.pojo.vo.ReadRecordVO;
import top.aiolife.record.service.IReadRecordService;
import top.aiolife.record.service.IFileService;
import top.aiolife.record.pojo.entity.FileEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReadRecordServiceImpl extends ServiceImpl<ReadRecordMapper, ReadRecordEntity> implements IReadRecordService {

    private final IFileService fileService;

    @Override
    public Page<ReadRecordVO> pageList(ReadRecordQuery query) {
        Long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<ReadRecordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReadRecordEntity::getUserId, userId);
        
        if (query.getType() != null) {
            wrapper.eq(ReadRecordEntity::getType, query.getType());
        }
        if (query.getStatus() != null) {
            wrapper.eq(ReadRecordEntity::getStatus, query.getStatus());
        } else if (Boolean.TRUE.equals(query.getActiveOnly())) {
            wrapper.in(ReadRecordEntity::getStatus, 0, 1);
        }
        if (StrUtil.isNotBlank(query.getTitle())) {
            wrapper.and(condition -> condition
                    .like(ReadRecordEntity::getTitle, query.getTitle())
                    .or()
                    .like(ReadRecordEntity::getAuthor, query.getTitle()));
        }
        wrapper.orderByAsc(ReadRecordEntity::getStatus)
               .orderByDesc(ReadRecordEntity::getFinishTime)
               .orderByDesc(ReadRecordEntity::getCreateTime);

        Page<ReadRecordEntity> page = new Page<>(query.getCurrent() == null ? 1 : query.getCurrent(), query.getSize() == null ? 10 : query.getSize());
        Page<ReadRecordEntity> entityPage = this.page(page, wrapper);

        Page<ReadRecordVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());

        List<ReadRecordVO> voList = entityPage.getRecords().stream().map(entity -> {
            ReadRecordVO vo = new ReadRecordVO();
            BeanUtil.copyProperties(entity, vo);
            vo.setId(String.valueOf(entity.getId()));
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    public void saveRecord(ReadRecordReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        ensureCoverUploaded(req);
        ReadRecordEntity entity = new ReadRecordEntity();
        BeanUtil.copyProperties(req, entity);
        entity.setUserId(userId);
        entity.setCreateUser(userId);
        entity.setUpdateUser(userId);
        
        if (entity.getStatus() != null && entity.getStatus() == 1 && entity.getStartTime() == null) {
            entity.setStartTime(LocalDateTime.now());
        }
        if (entity.getStatus() != null && entity.getStatus() == 2 && entity.getFinishTime() == null) {
            entity.setFinishTime(LocalDateTime.now());
        }
        
        this.save(entity);
    }

    @Override
    public void updateRecord(ReadRecordReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        ensureCoverUploaded(req);
        ReadRecordEntity entity = this.getById(req.getId());
        if (entity == null || !entity.getUserId().equals(userId)) {
            throw new RuntimeException("记录不存在或无权限");
        }
        
        BeanUtil.copyProperties(req, entity);
        entity.setUpdateUser(userId);
        
        if (entity.getStatus() != null && entity.getStatus() == 1 && entity.getStartTime() == null) {
            entity.setStartTime(LocalDateTime.now());
        }
        if (entity.getStatus() != null && entity.getStatus() == 2 && entity.getFinishTime() == null) {
            entity.setFinishTime(LocalDateTime.now());
        }
        
        this.updateById(entity);
    }

    @Override
    public void deleteRecord(Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        ReadRecordEntity entity = this.getById(id);
        if (entity != null && entity.getUserId().equals(userId)) {
            this.removeById(id);
        }
    }

    @Override
    public ReadRecordReq parseDouban(String url) {
        ReadRecordReq res = new ReadRecordReq();
        res.setUrl(url);
        res.setType(1); // 默认为书籍
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .timeout(5000)
                    .get();
            
            // 获取标题
            Element titleElement = doc.selectFirst("meta[property=og:title]");
            if (titleElement != null) {
                res.setTitle(titleElement.attr("content"));
            } else {
                Element h1 = doc.selectFirst("h1 span");
                if (h1 != null) {
                    res.setTitle(h1.text());
                }
            }
            
            // 尝试多种方式获取封面
            String coverUrl = null;
            
            // 方式1: og:image meta标签
            Element imageElement = doc.selectFirst("meta[property=og:image]");
            if (imageElement != null) {
                coverUrl = imageElement.attr("content");
            }
            
            // 方式2: mainpic区域图片
            if (StrUtil.isBlank(coverUrl)) {
                Element img = doc.selectFirst("#mainpic a img");
                if (img != null) {
                    coverUrl = img.attr("src");
                }
            }
            
            // 方式3: article-profile区域图片(网文)
            if (StrUtil.isBlank(coverUrl)) {
                Element articleImg = doc.selectFirst(".article-profile img");
                if (articleImg != null) {
                    coverUrl = articleImg.attr("src");
                }
            }
            
            if (StrUtil.isNotBlank(coverUrl)) {
                // 如果是豆瓣图片，尝试替换为更高清的版本
                coverUrl = coverUrl.replace("s/public", "l/public")
                                 .replace("s/pic", "l/pic");
                res.setCoverImgUrl(coverUrl);
            }

            // 下载封面图并上传到 MinIO，解决豆瓣 CDN 防盗链问题
            ensureCoverUploaded(res);

            // 获取作者
            Element authorElement = doc.selectFirst("meta[property=book:author]");
            if (authorElement != null) {
                res.setAuthor(authorElement.attr("content"));
            } else {
                Element authorSpan = doc.selectFirst("#info span:contains(作者)");
                if (authorSpan != null && authorSpan.nextElementSibling() != null) {
                    res.setAuthor(authorSpan.nextElementSibling().text());
                }
            }

            // 获取页数
            Element pageSpan = doc.selectFirst("#info span.pl:contains(页数)");
            if (pageSpan != null && pageSpan.nextSibling() != null) {
                String pageText = pageSpan.nextSibling().toString().trim();
                try {
                    res.setTotalProgress(Integer.parseInt(pageText));
                } catch (NumberFormatException e) {
                    log.warn("解析豆瓣页数失败: {}", pageText);
                }
            }
            
        } catch (Exception e) {
            log.error("解析豆瓣链接失败: {}", url, e);
            throw new RuntimeException("解析豆瓣链接失败，请检查链接是否正确或稍后重试");
        }
        return res;
    }

    private void ensureCoverUploaded(ReadRecordReq res) {
        if (StrUtil.isBlank(res.getCoverImgUrl()) || StrUtil.isNotBlank(res.getFileId())) {
            return;
        }
        try {
            var fileVO = fileService.uploadFromUrl(res.getCoverImgUrl(), top.aiolife.record.enums.FileBizType.READ_RECORD);
            res.setFileId(fileVO.getId());
            log.info("封面图已上传至 MinIO: fileId={}", fileVO.getId());
        } catch (Exception e) {
            log.warn("封面图上传 MinIO 失败，保留原始 URL: {}", res.getCoverImgUrl(), e);
        }
    }

    @Override
    public java.util.List<ReadRecordVO> listActive() {
        Long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReadRecordEntity> wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(ReadRecordEntity::getUserId, userId);
        wrapper.in(ReadRecordEntity::getStatus, 0, 1); // 未开始, 进行中
        wrapper.orderByDesc(ReadRecordEntity::getUpdateTime);
        
        java.util.List<ReadRecordEntity> entities = this.list(wrapper);
        return entities.stream().map(entity -> {
            ReadRecordVO vo = new ReadRecordVO();
            cn.hutool.core.bean.BeanUtil.copyProperties(entity, vo);
            vo.setId(String.valueOf(entity.getId()));
            return vo;
        }).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public ReadRecordVO getVOById(Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        ReadRecordEntity entity = this.lambdaQuery()
                .eq(ReadRecordEntity::getId, id)
                .eq(ReadRecordEntity::getUserId, userId)
                .one();
        if (entity == null) return null;
        ReadRecordVO vo = new ReadRecordVO();
        BeanUtil.copyProperties(entity, vo);
        vo.setId(String.valueOf(entity.getId()));
        return vo;
    }
}
