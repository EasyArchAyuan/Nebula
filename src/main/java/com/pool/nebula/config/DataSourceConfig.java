package com.pool.nebula.config;

import lombok.Data;

/**
 * 数据源基础配置
 *
 * @author Ayuan
 */
@Data
public class DataSourceConfig {

    /**
     * 驱动类
     */
    protected String driverClass;

    /**
     * jdbc url
     */
    protected String jdbcUrl;

    /**
     * 用户名
     */
    protected String user;

    /**
     * 密码
     */
    protected String password;
}