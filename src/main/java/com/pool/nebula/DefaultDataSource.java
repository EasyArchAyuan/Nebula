package com.pool.nebula;


import com.pool.nebula.config.PooledDataSourceConfig;
import com.pool.nebula.connection.NebulaPooledConnection;
import com.pool.nebula.connection.PooledConnection;
import com.pool.nebula.exception.ConnectPoolException;
import com.pool.nebula.util.DriverUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 数据源
 * @author Ayuan
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class DefaultDataSource extends AbstractDataSource implements NebulaPooledDataSource, NebulaLifeCycle {

    /**
     * 数据源相关配置
     */
    private PooledDataSourceConfig dataSourceConfig;

    /**
     * 池化连接列表
     */
    private List<NebulaPooledConnection> pooledConnectionList = new ArrayList<>();

    /**
     * 初始化
     */
    @Override
    public synchronized void init() {
        if (!checkConfigLegal()) {
            return;
        }
        //加载驱动类
        DriverUtil.loadDriverClass(dataSourceConfig.getDriverClass(), dataSourceConfig.getJdbcUrl());
        //连接池初始化
        this.initJdbcPool();
        //空闲连接校验
        initTestOnIdle();
    }

    @Override
    public synchronized NebulaPooledConnection getConnection() throws SQLException {
        logConnPoolDigest(pooledConnectionList);
        //1. 获取第一个不是 busy 的连接
        Optional<NebulaPooledConnection> connectionOptional = getFreeConnectionFromPool();
        if (connectionOptional.isPresent()) {
            return connectionOptional.get();
        }
        //2. 考虑是否可以扩容
        if (this.pooledConnectionList.size() >= this.dataSourceConfig.getMaxSize()) {
            //2.1 立刻返回
            if (this.dataSourceConfig.getMaxWaitMills() <= 0) {
                throw new ConnectPoolException("从连接池中获取失败");
            }
            try {
                wait(this.dataSourceConfig.getMaxWaitMills());
                return getConnection();
            } catch (InterruptedException exception) {
                exception.printStackTrace();
                throw new SQLException("等待空闲连接异常");
            }
        } else {
            //3. 扩容（暂时只扩容一个）
            NebulaPooledConnection pooledConnection = createPooledConnection();
            pooledConnection.setBusy(true);
            this.pooledConnectionList.add(pooledConnection);
            logConnPoolDigest(pooledConnectionList);
            return pooledConnection;
        }
    }

    @Override
    public synchronized void returnConnection(NebulaPooledConnection pooledConnection) {
        // 验证状态
        if (this.dataSourceConfig.isTestOnReturn()) {
            checkValid(pooledConnection);
        }
        // 设置为不繁忙
        pooledConnection.setBusy(false);
        logConnPoolDigest(pooledConnectionList);
        //通知其他线程
        notifyAll();
    }

    /**
     * 获取空闲的连接
     *
     * @return 连接
     * @since 1.3.0
     */
    private Optional<NebulaPooledConnection> getFreeConnectionFromPool() {
        for (NebulaPooledConnection pc : this.pooledConnectionList) {
            if (!pc.isBusy()) {
                pc.setBusy(true);
                // 验证有效性
                if (this.dataSourceConfig.isTestOnBorrow()) {
                    checkValid(pc);
                }
                return Optional.of(pc);
            }
        }
        return Optional.empty();
    }

    /**
     * 空闲时校验
     */
    private void initTestOnIdle() {
        if (StringUtils.isNotBlank(this.dataSourceConfig.getValidQuery())) {
            ScheduledExecutorService idleExecutor = Executors.newSingleThreadScheduledExecutor();
            idleExecutor.scheduleAtFixedRate(this::testOnIdleCheck, this.dataSourceConfig.getTestOnIdleIntervalSeconds(),
                    this.dataSourceConfig.getTestOnIdleIntervalSeconds(), TimeUnit.SECONDS);
        }
    }

    /**
     * 验证空闲连接是否有效
     */
    private void testOnIdleCheck() {
        for (NebulaPooledConnection pc : this.pooledConnectionList) {
            if (!pc.isBusy()) {
                checkValid(pc);
            }
        }
    }

    /**
     * 校验连接是否成功
     *
     * @param pooledConnection 池化连接
     */
    private void checkValid(final NebulaPooledConnection pooledConnection) {
        if (StringUtils.isNotBlank(this.dataSourceConfig.getValidQuery())) {
            Connection connection = pooledConnection.getConnection();
            try {
                // 如果连接无效，重新申请一个新的替代
                if (!connection.isValid(this.dataSourceConfig.getValidTimeOutSeconds())) {
                    Connection newConnection = createConnection();
                    pooledConnection.setConnection(newConnection);
                }
            } catch (SQLException throwable) {
                throw new ConnectPoolException(throwable);
            }
        }
    }

    /**
     * 初始化连接池
     */
    private void initJdbcPool() {
        final int minSize = this.dataSourceConfig.getMinSize();
        pooledConnectionList = new ArrayList<>(minSize);

        for (int i = 0; i < minSize; i++) {
            pooledConnectionList.add(createPooledConnection());
        }
    }

    /**
     * 创建一个池化的连接
     */
    private NebulaPooledConnection createPooledConnection() {
        Connection connection = createConnection();
        NebulaPooledConnection pooledConnection = new PooledConnection();
        pooledConnection.setBusy(false);
        pooledConnection.setConnection(connection);
        pooledConnection.setDataSource(this);
        return pooledConnection;
    }

    /**
     * 创建一个新连接
     *
     * @return 连接
     */
    private Connection createConnection() {
        try {
            return DriverManager.getConnection(this.dataSourceConfig.getJdbcUrl(),
                    this.dataSourceConfig.getUser(), this.dataSourceConfig.getPassword());
        } catch (SQLException e) {
            throw new ConnectPoolException(e);
        }
    }

    /**
     * 检查数据源配置是否合法
     */
    private boolean checkConfigLegal() {
        if (this.dataSourceConfig == null) {
            throw new ConnectPoolException("数据源配置缺失");
        }
        if (StringUtils.isBlank(dataSourceConfig.getDriverClass())
                && StringUtils.isBlank(this.dataSourceConfig.getJdbcUrl())) {
            throw new ConnectPoolException("数据源配置缺失");
        }
        return true;
    }

    /**
     * 日志打印
     */
    private static void logConnPoolDigest(List<NebulaPooledConnection> pooledConnectionList) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("pooledConnectionList status:[").append(pooledConnectionList.size()).append(",busy status:[");
        for (NebulaPooledConnection pooledConnection : pooledConnectionList) {
            stringBuilder.append(pooledConnection.isBusy()).append(",");
        }
        stringBuilder.append("]]");
        log.info(stringBuilder.toString());
    }
}
