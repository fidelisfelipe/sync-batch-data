package com.fidelis.syncbatch.util;

/**
 * ThreadLocal holder for dynamic datasource routing key.
 * Each thread (batch step partition thread) carries its own datasource key
 * so routing decisions are isolated from other concurrent jobs.
 */
public final class DataSourceContextHolder {

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    private DataSourceContextHolder() {}

    public static void setDataSourceKey(String key) {
        CONTEXT.set(key);
    }

    public static String getDataSourceKey() {
        return CONTEXT.get();
    }

    public static void clearDataSourceKey() {
        CONTEXT.remove();
    }

    /** Convenience: run block under a specific datasource key, then restore previous. */
    public static <T> T withDataSource(String key, DataSourceSupplier<T> supplier) throws Exception {
        String previous = getDataSourceKey();
        try {
            setDataSourceKey(key);
            return supplier.get();
        } finally {
            if (previous == null) {
                clearDataSourceKey();
            } else {
                setDataSourceKey(previous);
            }
        }
    }

    @FunctionalInterface
    public interface DataSourceSupplier<T> {
        T get() throws Exception;
    }
}
