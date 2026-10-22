package top.aiolife.capture.service;

import static top.aiolife.capture.service.CaptureValues.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import top.aiolife.record.mapper.IFileMapper;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.service.UserDictDataService;
import top.aiolife.sso.service.SecondaryLockGuard;
import top.aiolife.wardrobe.mapper.WardrobeCategoryMapper;
import top.aiolife.wardrobe.pojo.entity.WardrobeCategoryEntity;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CaptureSecurity {
    private final SecondaryLockGuard locks;
    private final IFileMapper files;
    private final UserDictDataService dicts;
    private final WardrobeCategoryMapper categories;

    public void menu(long user, String menu) {
        locks.checkMenus(user, menu);
    }

    public void file(long user, String id, String biz) {
        if (id == null || id.isBlank()) return;
        FileEntity f =
                files.selectOne(
                        new LambdaQueryWrapper<FileEntity>()
                                .eq(FileEntity::getId, id)
                                .eq(FileEntity::getCreateUser, user));
        if (f == null || !biz.equals(f.getBizType())) throw bad("图片不存在、业务类型不匹配或无权使用");
    }

    public List<UserDictDataEntity> dictionaries(long user, String type) {
        return dicts.listUserVisibleDictData(user, type);
    }

    public void dictionary(long user, String type, Long id) {
        if (id != null && dictionaries(user, type).stream().noneMatch(d -> id.equals(d.getId())))
            throw bad("分类或支付方式不存在或无权使用");
    }

    public List<WardrobeCategoryEntity> categories(long user) {
        return categories.selectList(
                new LambdaQueryWrapper<WardrobeCategoryEntity>()
                        .and(
                                w ->
                                        w.eq(WardrobeCategoryEntity::getCategoryType, 0)
                                                .or(
                                                        o ->
                                                                o.eq(
                                                                                WardrobeCategoryEntity
                                                                                        ::getCategoryType,
                                                                                1)
                                                                        .eq(
                                                                                WardrobeCategoryEntity
                                                                                        ::getUserId,
                                                                                user))));
    }

    public void category(long user, Long id) {
        if (id != null && categories(user).stream().noneMatch(c -> id.equals(c.getId())))
            throw bad("衣柜分类不存在或无权使用");
    }

    public List<Map<String, String>> options(long user, String type, boolean useValue) {
        return dictionaries(user, type).stream()
                .map(
                        d ->
                                Map.of(
                                        "id",
                                        useValue ? d.getDictValue() : d.getId().toString(),
                                        "name",
                                        d.getDictLabel()))
                .toList();
    }
}
