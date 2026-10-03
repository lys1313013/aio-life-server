package top.aiolife.record.autotask;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.aiolife.record.service.VideoCoverWorker;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="aio.life.scheduling.enabled", havingValue="true", matchIfMissing=true)
public class VideoCoverImportTask {
    private final VideoCoverWorker worker;
    @Scheduled(fixedDelayString="${aio.life.server.video-cover.poll-ms:5000}", initialDelay=15000)
    public void execute() { worker.runBatch(); }
}
