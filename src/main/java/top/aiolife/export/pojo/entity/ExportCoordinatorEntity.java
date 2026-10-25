package top.aiolife.export.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

import top.aiolife.core.pojo.entity.BaseEntity;

/** 导出持久化记录；只在存储层使用，不直接返回客户端。 */
@Getter
@Setter
@TableName("data_export_coordinator")
public class ExportCoordinatorEntity extends BaseEntity {
    private String name;
}
