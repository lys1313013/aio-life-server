package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;
import top.aiolife.record.enums.FileBizType;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.pojo.vo.FileVO;

import java.util.List;

public interface IFileService extends IService<FileEntity> {

    /**
     * 上传并保存文件记录
     *
     * @param file       文件对象
     * @param bizType    业务类型
     * @return 文件信息
     */
    FileVO upload(MultipartFile file, FileBizType bizType);

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
    List<FileVO> getByBiz(String bizType, Long bizId);
    
    /**
     * 从 URL 下载文件并上传保存
     *
     * @param imageUrl 图片 URL
     * @param bizType  业务类型
     * @return 文件信息
     */
    FileVO uploadFromUrl(String imageUrl, FileBizType bizType);

    /**
     * 将 Entity 转换为 VO
     */
    FileVO toVO(FileEntity entity);
}
