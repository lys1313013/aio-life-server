package top.aiolife.wardrobe.convertor;

import org.mapstruct.*;
import org.mapstruct.factory.Mappers;
import top.aiolife.wardrobe.pojo.req.CategoryReq;
import top.aiolife.wardrobe.pojo.req.WardrobeCategorySaveReq;
import top.aiolife.wardrobe.pojo.req.WardrobeItemReq;
import top.aiolife.wardrobe.pojo.req.WardrobeItemSaveReq;

@Mapper(builder = @Builder(disableBuilder = true))
public interface WardrobeApiConvertor {
    WardrobeApiConvertor INSTANCE = Mappers.getMapper(WardrobeApiConvertor.class);
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    WardrobeItemReq fromWardrobeItemSaveReq(WardrobeItemSaveReq request);
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    CategoryReq fromWardrobeCategorySaveReq(WardrobeCategorySaveReq request);
}
