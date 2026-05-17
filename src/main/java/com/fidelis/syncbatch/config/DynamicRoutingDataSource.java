package com.fidelis.syncbatch.config;

import com.fidelis.syncbatch.util.DataSourceContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Routes JDBC connections to the correct datasource based on the ThreadLocal key
 * stored in {@link DataSourceContextHolder}.
 *
 * <p>When no key is set (returns null), Spring falls back to the default datasource
 * (local), ensuring normal JPA/repository operations are unaffected.
 */
@Slf4j
public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        String key = DataSourceContextHolder.getDataSourceKey();
        log.debug("Routing datasource key: {}", key);
        return key;
    }
}
