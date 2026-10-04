package top.aiolife.record.provider.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.aiolife.record.pojo.vo.DashboardCardVO;
import top.aiolife.record.service.IUserBindService;
import top.aiolife.record.pojo.entity.UserBindEntity;
import com.alibaba.fastjson2.JSONObject;

@Tag("external")
@SpringBootTest
public class GithubCardProviderTest {

    @Autowired
    private GithubCardProvider githubCardProvider;

    @Autowired
    private IUserBindService userBindService;

    @Test
    public void testGetCard() {
        DashboardCardVO card = githubCardProvider.getCard(1L);
        System.out.println("CARD: " + card);
    }
}
