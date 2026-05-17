package com.fidelis.syncbatch.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DataSourceContextHolderTest {

    @AfterEach
    void cleanup() {
        DataSourceContextHolder.clearDataSourceKey();
    }

    @Test
    void setAndGet_shouldReturnExpectedKey() {
        DataSourceContextHolder.setDataSourceKey("source1");
        assertThat(DataSourceContextHolder.getDataSourceKey()).isEqualTo("source1");
    }

    @Test
    void clear_shouldRemoveKey() {
        DataSourceContextHolder.setDataSourceKey("source1");
        DataSourceContextHolder.clearDataSourceKey();
        assertThat(DataSourceContextHolder.getDataSourceKey()).isNull();
    }

    @Test
    void withDataSource_shouldRestoreKeyAfterBlock() throws Exception {
        DataSourceContextHolder.setDataSourceKey("local");

        String result = DataSourceContextHolder.withDataSource("source1", () -> {
            assertThat(DataSourceContextHolder.getDataSourceKey()).isEqualTo("source1");
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(DataSourceContextHolder.getDataSourceKey()).isEqualTo("local");
    }

    @Test
    void withDataSource_shouldClearKeyWhenNoPreviousKeySet() throws Exception {
        DataSourceContextHolder.withDataSource("source2", () -> {
            assertThat(DataSourceContextHolder.getDataSourceKey()).isEqualTo("source2");
            return null;
        });

        assertThat(DataSourceContextHolder.getDataSourceKey()).isNull();
    }

    @Test
    void threadIsolation_shouldIsolateKeyPerThread() throws InterruptedException {
        DataSourceContextHolder.setDataSourceKey("main-thread");

        Thread thread = new Thread(() -> {
            assertThat(DataSourceContextHolder.getDataSourceKey())
                    .as("Thread should not inherit parent's key")
                    .isNull();
            DataSourceContextHolder.setDataSourceKey("child-thread");
            assertThat(DataSourceContextHolder.getDataSourceKey()).isEqualTo("child-thread");
        });

        thread.start();
        thread.join();

        // Main thread key should be unaffected
        assertThat(DataSourceContextHolder.getDataSourceKey()).isEqualTo("main-thread");
    }
}
