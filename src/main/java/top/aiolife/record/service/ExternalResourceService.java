package top.aiolife.record.service;

import java.io.InterruptedIOException;
import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import org.springframework.dao.DataAccessException;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import top.aiolife.record.enums.FileBizType;
import top.aiolife.core.util.FileContentPolicy;
import top.aiolife.record.mapper.ExternalResourceMapper;
import top.aiolife.record.pojo.entity.ExternalResourceEntity;

/** 来源身份、独立下载租约及失败重试；来源成功后不再下载。 */
@Service
public class ExternalResourceService {
    private final ExternalResourceMapper sources;
    private final VideoCoverDownloader downloader;
    private final DoubanCoverUrlPolicy douban;
    private final VideoCoverStorage storage;
    private final TransactionTemplate transactions;
    private static final Pattern DOUBAN_PATH = Pattern.compile("^/view/photo/([^/]+)/(?:public/)?(p[0-9]+)\\.[A-Za-z0-9]+$");
    private static final Pattern DOUBAN_BOOK_PATH = Pattern.compile("^/view/subject/([^/]+)/public/(s[0-9]+)\\.[A-Za-z0-9]+$");

    public ExternalResourceService(ExternalResourceMapper sources, VideoCoverDownloader downloader,
            DoubanCoverUrlPolicy douban, VideoCoverStorage storage, PlatformTransactionManager manager) {
        this.sources=sources; this.downloader=downloader; this.douban=douban; this.storage=storage;
        transactions=new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ExternalResourceEntity video(String url, long owner) { return register(identity(url, null), owner); }

    public ExternalResourceEntity image(String url, FileBizType type, long owner) {
        var source=register(identity(url, type), owner);
        if (source.getStorageObjectId()!=null) return source;
        // 同步入口再次请求即可重试失败来源；不重新抓取成功来源。
        if ("FAILED".equals(source.getState()) && source.getAttempts()>=5) retryFailed(source.getId());
        fetch(source.getId());
        long sourceId=source.getId();
        source=transactions.execute(status->sources.selectById(sourceId));
        if (source==null || source.getStorageObjectId()==null) throw new IllegalStateException("封面暂未导入成功，请稍后重试");
        return source;
    }

    private ExternalResourceEntity register(ExternalResourceEntity resource,long owner) {
        return transactions.execute(status -> {
            resource.setId(IdWorker.getId()); resource.fillCreateCommonField(owner);
            sources.register(resource);
            return sources.identity(resource);
        });
    }

    public ExternalResourceEntity identity(String raw,FileBizType type) {
        var result=new ExternalResourceEntity();
        if (type==null) {
            String url=downloader.normalize(raw);
            String path=URI.create(url).getRawPath();
            String[] parts=path.substring(1).split("@",2);
            result.setPlatform("bilibili"); result.setResourceType("video_cover");
            result.setExternalKey(parts[0].replaceFirst("\\.[A-Za-z0-9]+$", ""));
            result.setVariant(parts.length==2?parts[1]:""); result.setSourceUrl(url);
        } else {
            if (type!=FileBizType.MOVIE && type!=FileBizType.READ_RECORD) throw new IllegalArgumentException("不支持的远程图片业务");
            String url=douban.validate(raw);
            String path=URI.create(url).getRawPath();
            var match=DOUBAN_PATH.matcher(path);
            // 图书使用 subject/s 标识，电影海报使用 photo/p 标识；都保留 CDN 的稳定图片身份。
            if (!match.matches() && type==FileBizType.READ_RECORD) match=DOUBAN_BOOK_PATH.matcher(path);
            if (!match.matches()) throw new IllegalArgumentException("不支持的豆瓣封面路径");
            result.setPlatform("douban"); result.setResourceType(type==FileBizType.MOVIE?"movie_poster":"book_cover");
            result.setExternalKey(match.group(2)); result.setVariant(match.group(1)); result.setSourceUrl(url);
        }
        if(result.getExternalKey().length()>255 || result.getVariant().length()>128)
            throw new IllegalArgumentException("来源标识过长");
        return result;
    }

    public void fetch(long id) {
        String token=UUID.randomUUID().toString().replace("-", "");
        var source=transactions.execute(status->{
            if(sources.claim(id,token)!=1) return null;
            return sources.selectById(id);
        });
        if(source==null) return;
        String phase="DOWNLOAD";
        try {
            byte[] bytes="douban".equals(source.getPlatform())
                ?downloader.downloadDouban(douban.validate(source.getSourceUrl())):downloader.download(source.getSourceUrl());
            var image=FileContentPolicy.requireImage(bytes);
            phase="STORE";
            transactions.executeWithoutResult(status -> {
                var claimed=sources.lockClaim(id,token);
                if(claimed==null) return;
                var object=storage.store(bytes,claimed.getCreateUser(),image);
                sources.update(null,new LambdaUpdateWrapper<ExternalResourceEntity>().eq(ExternalResourceEntity::getId,id)
                    .set(ExternalResourceEntity::getStorageObjectId,object.getId()).set(ExternalResourceEntity::getState,"READY")
                    .set(ExternalResourceEntity::getAttempts,0).set(ExternalResourceEntity::getFetchedAt,LocalDateTime.now())
                    .set(ExternalResourceEntity::getErrorCode,null).set(ExternalResourceEntity::getNextAttemptAt,null)
                    .set(ExternalResourceEntity::getLeaseToken,null).set(ExternalResourceEntity::getLeaseUntil,null)
                    .set(ExternalResourceEntity::getUpdateTime,LocalDateTime.now()));
            });
        } catch(Exception exception) {
            String error=failureCode(phase,exception);
            transactions.executeWithoutResult(status -> {
                var claimed=sources.lockClaim(id,token);
                if(claimed==null) return;
                String code=error;
                sources.update(null,new LambdaUpdateWrapper<ExternalResourceEntity>().eq(ExternalResourceEntity::getId,id)
                    .set(ExternalResourceEntity::getState,"FAILED").set(ExternalResourceEntity::getErrorCode,code)
                    .set(ExternalResourceEntity::getNextAttemptAt,LocalDateTime.now().plusSeconds(Math.min(300L,5L<<claimed.getAttempts())))
                    .set(ExternalResourceEntity::getLeaseToken,null).set(ExternalResourceEntity::getLeaseUntil,null)
                    .set(ExternalResourceEntity::getUpdateTime,LocalDateTime.now()));
            });
        }
    }

    public void retryFailed(long id) {
        transactions.executeWithoutResult(status->{
            var source=sources.selectById(id);
            if(source!=null && source.getStorageObjectId()==null && "FAILED".equals(source.getState()))
                sources.retry(id,source.getSourceUrl());
        });
    }
    /** 只持久化受控错误码，不保留含签名、地址或SQL的异常文本。 */
    private static String failureCode(String phase,Exception exception) {
        if(exception instanceof DataAccessException) return "IMAGE_DATABASE_FAILED";
        if("STORE".equals(phase)) return "IMAGE_STORAGE_FAILED";
        for(Throwable cause=exception;cause!=null;cause=cause.getCause()) {
            if(cause instanceof UnknownHostException) return "IMAGE_DNS_FAILED";
            if(cause instanceof SocketTimeoutException) return "IMAGE_DOWNLOAD_TIMEOUT";
            if(cause instanceof ConnectException) return "IMAGE_CONNECT_FAILED";
            if(cause instanceof SSLException) return "IMAGE_TLS_FAILED";
            if(cause instanceof InterruptedIOException) return "IMAGE_DOWNLOAD_TIMEOUT";
        }
        String message=exception.getMessage();
        if(exception instanceof IllegalStateException && message!=null && message.matches("IMAGE_HTTP_[0-9]{3}")) return message;
        return exception instanceof IllegalArgumentException?"INVALID_IMAGE_SOURCE":"IMAGE_DOWNLOAD_FAILED";
    }
}
