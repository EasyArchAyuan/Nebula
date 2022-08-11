package com.pool.nebula.connection;


import com.pool.nebula.NebulaPooledDataSource;

import java.sql.Connection;

/**
 * 池化连接接口定义
 * 扩展Connection
 *
 * @author Ayuan
 */
public interface NebulaPooledConnection extends Connection {

    /**
     * 是否繁忙
     */
    boolean isBusy();

    /**
     * 设置是否繁忙状态
     */
    void setBusy(boolean busy);

    /**
     * 获取真正的连接
     */
    Connection getConnection();

    /**
     * 设置连接信息
     */
    void setConnection(Connection connection);

    /**
     * 设置数据源信息
     */
    void setDataSource(final NebulaPooledDataSource dataSource);
}