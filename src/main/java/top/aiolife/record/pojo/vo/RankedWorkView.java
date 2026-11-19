package top.aiolife.record.pojo.vo;

import java.util.List;

public interface RankedWorkView {
    String getPlatform();
    String getExternalKey();
    void setCreativeWorkId(Long id);
    void setRetryCoverWorkId(Long id);
    void setRankings(List<RankingBadgeVO> rankings);
    void setCoverImgUrl(String url);
}
