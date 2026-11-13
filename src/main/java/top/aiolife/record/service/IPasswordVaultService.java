package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;
import top.aiolife.record.pojo.entity.PasswordVaultEntity;

/**
 * 密码库Service接口
 *
 * @author Lys
 * @date 2026/04/28
 */
public interface IPasswordVaultService extends IService<PasswordVaultEntity> {

    List<PasswordVaultEntity> listForUser(long userId);

    PasswordVaultEntity getOwned(long userId, long id);

    void createForUser(long userId, PasswordVaultEntity entity);

    void updateForUser(long userId, long id, PasswordVaultEntity entity);

    void deleteForUser(long userId, long id);

    List<String> listCategories(long userId);
}
