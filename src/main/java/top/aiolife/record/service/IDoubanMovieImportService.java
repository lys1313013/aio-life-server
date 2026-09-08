package top.aiolife.record.service;

import top.aiolife.record.pojo.req.DoubanMovieImportReq;
import top.aiolife.record.pojo.vo.DoubanMovieImportPreviewVO;
import top.aiolife.record.pojo.vo.DoubanMovieImportResultVO;

public interface IDoubanMovieImportService {

    DoubanMovieImportPreviewVO preview(DoubanMovieImportReq request);

    DoubanMovieImportResultVO importRecords(DoubanMovieImportReq request);
}
