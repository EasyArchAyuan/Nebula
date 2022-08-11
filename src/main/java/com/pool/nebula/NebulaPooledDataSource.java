package com.pool.nebula;


import com.pool.nebula.connection.NebulaPooledConnection;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * 池化数据源接口
 *
 * @author Ayuan
 */
public interface NebulaPooledDataSource extends DataSource {

    /**
     * 获取连接
     */
    @Override
    NebulaPooledConnection getConnection() throws SQLException;

    /**
     * 归还连接
     */
    void returnConnection(NebulaPooledConnection pooledConnection);
}