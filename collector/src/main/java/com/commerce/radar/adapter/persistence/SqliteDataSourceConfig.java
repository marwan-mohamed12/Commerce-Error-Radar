package com.commerce.radar.adapter.persistence;

import com.commerce.radar.config.RadarProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Path;

@Configuration
public class SqliteDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(SqliteDataSourceConfig.class);

    @Bean
    DataSource dataSource(RadarProperties properties) throws IOException {
        Path db = SqliteFile.resolve(properties.getSqlitePath());
        String url = SqliteFile.jdbcUrl(db);
        log.info("Opening SQLite file {}", db);
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setMaximumPoolSize(1);
        ds.setPoolName("radar-sqlite");
        return ds;
    }
}
