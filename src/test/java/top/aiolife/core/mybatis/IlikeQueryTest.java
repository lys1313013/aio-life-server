package top.aiolife.core.mybatis;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IlikeQueryTest {

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(SampleEntity.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SampleEntity.class);
        }
    }

    @Test
    void anyOf_生成字段级ILIKE条件() {
        LambdaQueryWrapper<SampleEntity> wrapper = new LambdaQueryWrapper<>();
        IlikeQuery.anyOf(wrapper, "keyword", SampleEntity::getTitle, SampleEntity::getTags);
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("title ILIKE"), sql);
        assertTrue(sql.contains("tags ILIKE"), sql);
        assertTrue(sql.contains(" OR "), sql);
        assertTrue(wrapper.getParamNameValuePairs().containsValue("%keyword%"), sql);
    }

    @Test
    void anyOf_条件不成立时不追加() {
        LambdaQueryWrapper<SampleEntity> wrapper = new LambdaQueryWrapper<>();
        IlikeQuery.anyOf(wrapper, false, "keyword", SampleEntity::getTitle);
        assertFalse(wrapper.getSqlSegment().contains("ILIKE"));
    }

    @Test
    void lowerEquals_使用lower函数比较() {
        LambdaQueryWrapper<SampleEntity> wrapper = new LambdaQueryWrapper<>();
        IlikeQuery.lowerEquals(wrapper, SampleEntity::getTitle, "MixedCase");
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("lower(title) = lower("), sql);
        assertTrue(wrapper.getParamNameValuePairs().containsValue("MixedCase"), sql);
    }

    @TableName("sample_entity")
    static class SampleEntity {

        private String title;

        private String tags;

        public String getTitle() {
            return title;
        }

        public String getTags() {
            return tags;
        }
    }
}
