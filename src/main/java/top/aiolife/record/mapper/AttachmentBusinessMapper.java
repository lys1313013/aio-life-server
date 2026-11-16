package top.aiolife.record.mapper;

import org.apache.ibatis.annotations.*;

/** 多态附件引用校验；表名只由固定用途白名单选择，不接收任意表名。 */
@Mapper
public interface AttachmentBusinessMapper {
    @Select("""
        <script>SELECT count(*) FROM
        <choose>
          <when test="type=='device_cover'">device</when>
          <when test="type=='movie_cover'">movie</when>
          <when test="type=='read_cover'">read_record</when>
          <when test="type=='wardrobe_item_cover'">wardrobe_item</when>
          <when test="type=='honor_record'">honor_record</when>
          <when test="type=='event_record'">event_record</when>
          <when test="type=='feedback'">feedback</when>
          <when test="type=='feedback_comment'">feedback_comment</when>
          <otherwise>sys_user</otherwise>
        </choose>
        WHERE id=#{id} AND is_deleted=0
        <if test="type!='avatar'">AND user_id=#{owner}</if>
        <if test="type=='avatar'">AND id=#{owner}</if>
        </script>
        """)
    long countOwned(String type,long id,long owner);
}
