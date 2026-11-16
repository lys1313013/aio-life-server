package top.aiolife.record.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.aiolife.record.mapper.ExternalResourceMapper;
import top.aiolife.record.mapper.ImageImportTaskMapper;

/** 来源持下载租约；等待任务无需领取，即使进程退出也可被下轮重复推进。 */
@Service
@RequiredArgsConstructor
public class VideoCoverWorker {
    private final ExternalResourceMapper sources;
    private final ExternalResourceService resources;
    private final ImageImportTaskMapper tasks;
    private final VideoCoverService service;
    public int runBatch() {
        int processed=0;
        for(var source:sources.due()) { resources.fetch(source.getId()); processed++; }
        for(var task:tasks.due()) { service.settle(task.getId()); processed++; }
        return processed;
    }
}
