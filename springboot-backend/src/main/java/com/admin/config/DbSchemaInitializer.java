package com.admin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 轻量级数据库结构自愈：为存量库补齐新增字段。
 *
 * <p>本项目没有引入 Flyway / Liquibase，{@code gost.sql} 只在数据库首次初始化时执行，
 * 因此升级镜像后老库不会自动获得新增列，接口会因 {@code Unknown column} 直接报错。
 * 这里在应用启动完成、任何请求进来之前做一次幂等的补列：
 * 先查 {@code information_schema.COLUMNS}，缺列才执行 ALTER TABLE。
 *
 * <p>失败只记日志、不阻断启动（例如账号没有 DDL 权限时，仍可由运维手动执行 SQL）。
 */
@Slf4j
@Component
@Order(1)
public class DbSchemaInitializer implements ApplicationRunner {

    @Resource
    private DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        ensureColumn("tunnel", "flow_reset_time",
                "ALTER TABLE `tunnel` ADD COLUMN `flow_reset_time` int(10) NOT NULL DEFAULT '1' " +
                        "COMMENT '流量重置日期：0=不重置，1-31=每月第几号'");
    }

    /**
     * 若表中缺少指定列则补上（幂等）
     */
    private void ensureColumn(String table, String column, String alterSql) {
        try (Connection conn = dataSource.getConnection()) {
            if (columnExists(conn, table, column)) {
                return;
            }
            try (Statement st = conn.createStatement()) {
                st.execute(alterSql);
                log.info("数据库结构自愈：已为表 {} 补充字段 {}", table, column);
            }
        } catch (Exception e) {
            log.error("数据库结构自愈失败（表 {} 字段 {}），如接口报 Unknown column 请手动执行：{}，原因：{}",
                    table, column, alterSql, e.getMessage());
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws Exception {
        String sql = "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}
