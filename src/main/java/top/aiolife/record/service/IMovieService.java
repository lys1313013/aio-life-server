package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import top.aiolife.record.pojo.entity.MovieEntity;
import top.aiolife.record.pojo.query.MovieQuery;
import top.aiolife.record.pojo.req.MovieReq;
import top.aiolife.record.pojo.vo.MovieVO;

public interface IMovieService extends IService<MovieEntity> {

    Page<MovieVO> pageList(MovieQuery query);

    Long saveRecord(MovieReq req);

    void updateRecord(MovieReq req);

    void deleteRecord(Long id);

    MovieReq parseDouban(String url);

    MovieEntity findByDoubanSubjectId(String subjectId);

    void updateCoverFileId(Long id, String fileId);

    java.util.List<MovieVO> listActive();

    MovieVO getVOById(Long id);
}
