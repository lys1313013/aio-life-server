package top.aiolife.system.homecard;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.aiolife.system.mapper.HomeCardPreferenceMapper;
import top.aiolife.system.pojo.entity.HomeCardPreferenceEntity;
import top.aiolife.system.service.HomeCardCatalog;
import top.aiolife.system.service.HomeCardPreferenceService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HomeCardPreferenceServiceTest {
    HomeCardPreferenceMapper mapper;
    HomeCardPreferenceService service;
    Map<Long, HomeCardPreferenceEntity> rows;
    long nextId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        mapper = mock(HomeCardPreferenceMapper.class);
        service = new HomeCardPreferenceService(mapper);
        rows = new HashMap<>(); nextId = 1;
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), HomeCardPreferenceEntity.class);
        when(mapper.lockUser(anyLong())).thenAnswer(call -> call.getArgument(0));
        when(mapper.selectList(any())).thenAnswer(call -> {
            LambdaQueryWrapper<HomeCardPreferenceEntity> query = call.getArgument(0);
            assertEquals("(user_id = #{ew.paramNameValuePairs.MPGENVAL1})", query.getSqlSegment());
            long owner = ((Number) query.getParamNameValuePairs().get("MPGENVAL1")).longValue();
            return rows.values().stream().filter(row -> row.getUserId() == owner).toList();
        });
        when(mapper.insert(any(HomeCardPreferenceEntity.class))).thenAnswer(call -> {
            HomeCardPreferenceEntity row = call.getArgument(0); row.setId(nextId++); rows.put(row.getId(), row); return 1;
        });
        when(mapper.updateById(any(HomeCardPreferenceEntity.class))).thenReturn(1);
        when(mapper.reset(anyLong())).thenAnswer(call -> { long user = call.getArgument(0); rows.values().removeIf(row -> row.getUserId() == user); return 1; });
    }

    @Test void defaults_读取不写库且两类卡片独立注册() {
        var cards = service.get(7);
        assertEquals(16, cards.size());
        assertEquals(5, cards.stream().filter(card -> card.group().equals("overview")).count());
        assertTrue(cards.stream().allMatch(card -> card.enabled()));
        verify(mapper, never()).insert(any(HomeCardPreferenceEntity.class));
    }
    @Test void toggle_用户隔离且不会连带隐藏同业务详情卡() {
        service.toggle(7, "overview.github", false);
        assertFalse(service.get(7).stream().filter(card -> card.cardKey().equals("overview.github")).findFirst().orElseThrow().enabled());
        assertTrue(service.get(7).stream().filter(card -> card.cardKey().equals("section.github")).findFirst().orElseThrow().enabled());
        assertTrue(service.get(8).stream().allMatch(card -> card.enabled()));
        var order = inOrder(mapper); order.verify(mapper).lockUser(7); order.verify(mapper).selectList(any()); order.verify(mapper).insert(any(HomeCardPreferenceEntity.class));
    }
    @Test void reorder_完整分组排序保留开关且不改另一组() {
        service.toggle(7, "overview.github", false);
        var keys = new ArrayList<>(HomeCardCatalog.ALL.stream().filter(card -> card.group().equals("overview")).map(HomeCardCatalog.Definition::cardKey).toList());
        Collections.reverse(keys);
        var result = service.reorder(7, "overview", keys);
        assertEquals(keys, result.stream().filter(card -> card.group().equals("overview")).map(card -> card.cardKey()).toList());
        assertFalse(result.stream().filter(card -> card.cardKey().equals("overview.github")).findFirst().orElseThrow().enabled());
        assertEquals(HomeCardCatalog.ALL.stream().filter(card -> card.group().equals("section")).map(HomeCardCatalog.Definition::cardKey).toList(), result.stream().filter(card -> card.group().equals("section")).map(card -> card.cardKey()).toList());
        service.toggle(7, "overview.github", true);
        assertEquals(keys, service.get(7).stream().filter(card -> card.group().equals("overview")).map(card -> card.cardKey()).toList());
    }
    @Test void invalid_未知重复遗漏跨组排序在写入前拒绝() {
        assertThrows(IllegalArgumentException.class, () -> service.toggle(7, "custom.sql", true));
        assertThrows(IllegalArgumentException.class, () -> service.reorder(7, "unknown", List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.reorder(7, "overview", List.of("overview.github")));
        assertThrows(IllegalArgumentException.class, () -> service.reorder(7, "overview", Collections.nCopies(5, "overview.github")));
        assertThrows(IllegalArgumentException.class, () -> service.reorder(7, "overview", List.of("overview.github", "overview.read", "overview.shanbay", "overview.exercise", "section.watched")));
        verify(mapper, never()).lockUser(anyLong());
    }
    @Test void reset_仅恢复当前用户且重复恢复后可再保存() {
        service.toggle(7, "section.goal", false); service.toggle(8, "section.goal", false);
        service.reset(7); service.reset(7); service.toggle(7, "section.reading", false);
        assertFalse(service.get(8).stream().filter(card -> card.cardKey().equals("section.goal")).findFirst().orElseThrow().enabled());
        assertTrue(service.get(7).stream().filter(card -> card.cardKey().equals("section.goal")).findFirst().orElseThrow().enabled());
        assertEquals(2, rows.size());
    }
    @Test void deletedUser_不能修改配置() {
        when(mapper.lockUser(9)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.toggle(9, "section.goal", true));
        verify(mapper, never()).insert(any(HomeCardPreferenceEntity.class));
    }
}
