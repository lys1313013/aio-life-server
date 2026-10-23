package top.aiolife.record.client;

/** 供应商明确报告额度耗尽，或命中服务端额度状态。 */
public final class MapQuotaExceededException extends MapFallbackException {
    private final int upstreamStatus;

    public MapQuotaExceededException() { this(302); }

    public MapQuotaExceededException(int upstreamStatus) {
        super("地图查询额度已用完，请稍后重试或手工选点");
        this.upstreamStatus = upstreamStatus;
    }

    public int upstreamStatus() { return upstreamStatus; }
}
