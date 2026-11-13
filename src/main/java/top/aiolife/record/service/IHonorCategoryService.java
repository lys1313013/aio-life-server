package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;
import top.aiolife.record.pojo.entity.HonorCategoryEntity;

/**
 * 荣誉分类服务接口
 *
 * @author Lys
 * @date 2026/04/11
 */
public interface IHonorCategoryService extends IService<HonorCategoryEntity> {

    /**
     * 查询当前用户可见的荣誉分类：本人分类与公共分类。
     */
    List<HonorCategoryEntity> listForUser(long userId);
}
