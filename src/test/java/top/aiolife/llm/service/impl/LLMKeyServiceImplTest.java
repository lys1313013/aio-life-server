package top.aiolife.llm.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.aiolife.llm.mapper.LLMKeyMapper;
import top.aiolife.llm.pojo.entity.LLMKeyEntity;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LLMKeyServiceImplTest {

    @Mock
    private LLMKeyMapper llmKeyMapper;

    @InjectMocks
    private LLMKeyServiceImpl llmKeyService;

    private LLMKeyEntity testKey;
    private Long testKeyId = 1L;
    private Long testUserId = 1L;

    @BeforeEach
    void setUp() {
        testKey = new LLMKeyEntity();
        testKey.setId(testKeyId);
        testKey.setUserId(testUserId);
        testKey.setModelName("gpt-4");
        testKey.setApiKey("test-api-key");
        testKey.setBaseUrl("https://api.openai.com/v1");
        testKey.setIsDefault(0);
    }

    @Test
    void testSaveLLMKey() {
        when(llmKeyMapper.insert(any(LLMKeyEntity.class))).thenReturn(1);

        llmKeyService.saveLLMKey(testKey);

        verify(llmKeyMapper, times(1)).insert(any(LLMKeyEntity.class));
        assertNotNull(testKey.getId());
        assertNotNull(testKey.getCreateTime());
        assertNotNull(testKey.getUpdateTime());
    }

    @Test
    void testUpdateLLMKey() {
        when(llmKeyMapper.selectById(testKeyId)).thenReturn(testKey);
        when(llmKeyMapper.updateById(any(LLMKeyEntity.class))).thenReturn(1);

        llmKeyService.updateLLMKey(testKey);

        verify(llmKeyMapper, times(1)).updateById(any(LLMKeyEntity.class));
        assertNotNull(testKey.getUpdateTime());
    }

    @Test
    void testUpdateLLMKeyWithoutPermission() {
        LLMKeyEntity otherUserKey = new LLMKeyEntity();
        otherUserKey.setId(testKeyId);
        otherUserKey.setUserId(999L);
        when(llmKeyMapper.selectById(testKeyId)).thenReturn(otherUserKey);

        assertThrows(RuntimeException.class, () -> llmKeyService.updateLLMKey(testKey));

        verify(llmKeyMapper, never()).updateById(any(LLMKeyEntity.class));
    }

    @Test
    void testDeleteLLMKey() {
        when(llmKeyMapper.delete(any())).thenReturn(1);

        llmKeyService.deleteLLMKey(testKeyId, testUserId);

        verify(llmKeyMapper, times(1)).delete(any());
    }

    @Test
    void testGetLLMKeyList() {
        List<LLMKeyEntity> expectedList = new ArrayList<>();
        expectedList.add(testKey);

        when(llmKeyMapper.selectByUserId(testUserId)).thenReturn(expectedList);

        List<LLMKeyEntity> result = llmKeyService.getLLMKeyList(testUserId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testKey.getId(), result.get(0).getId());
    }

    @Test
    void testGetDefaultLLMKey() {
        when(llmKeyMapper.selectDefaultByUserId(testUserId)).thenReturn(testKey);

        LLMKeyEntity result = llmKeyService.getDefaultLLMKey(testUserId);

        assertNotNull(result);
        assertEquals(testKey.getId(), result.getId());
    }
}
