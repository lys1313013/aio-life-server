package top.aiolife.export.service;

import java.util.Set;

public final class ExportStatus {
    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";
    public static final String REVOKED = "REVOKED";
    public static final Set<String> ACTIVE = Set.of(QUEUED, RUNNING);

    private ExportStatus() {}
}
