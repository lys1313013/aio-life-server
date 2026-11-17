package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;
import top.aiolife.record.enums.AttachmentBizType;
import top.aiolife.record.pojo.entity.AttachmentEntity;
import top.aiolife.record.pojo.vo.AttachmentVO;

import java.util.List;

public interface IAttachmentService extends IService<AttachmentEntity> {

    /** 仅按对外 UUIDv4 标识定位附件；内部主键不作为业务接口参数。 */
    AttachmentEntity getByKey(String fileKey);

    /**
     * 上传并保存文件记录
     *
     * @param file       文件对象
     * @param bizType    业务类型
     * @return 文件信息
     */
    AttachmentVO upload(MultipartFile file, AttachmentBizType bizType);

    /**
     * 绑定业务记录 ID
     *
     * @param fileIds 文件ID列表
     * @param bizType 业务类型
     * @param bizId   业务ID
     */
    void bindBizId(List<String> fileIds, String bizType, Long bizId);

    /**
     * 根据业务查询文件列表
     *
     * @param bizType 业务类型
     * @param bizId   业务ID
     * @return 文件列表VO
     */
    List<AttachmentVO> getByBiz(String bizType, Long bizId);
    
    /**
     * 从白名单豆瓣 CDN 的 HTTPS URL 下载阅读/观影封面，不跟随重定向。
     * 自定义封面使用 upload，保存业务记录时将返回的 fileId 绑定至 file.biz_type/biz_id。
     *
     * @param imageUrl 图片 URL
     * @param bizType  业务类型
     * @return 文件信息
     */
    AttachmentVO uploadFromUrl(String imageUrl, AttachmentBizType bizType);

    /**
     * 将 Entity 转换为 VO
     */
    AttachmentVO toVO(AttachmentEntity entity);
}
