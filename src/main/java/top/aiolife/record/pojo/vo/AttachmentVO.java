package top.aiolife.record.pojo.vo;

import lombok.Data;

@Data
public class AttachmentVO {

    /** 附件对外 UUIDv4，对应 attachment.file_key；不返回内部主键。 */
    private String id;

    private String fileName;

    private Long fileSize;

    private String fileType;

    private String fileUrl;
}
