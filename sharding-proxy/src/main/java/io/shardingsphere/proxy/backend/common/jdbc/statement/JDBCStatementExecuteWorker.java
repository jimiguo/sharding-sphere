/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingsphere.proxy.backend.common.jdbc.statement;

import io.shardingsphere.core.constant.SQLType;
import io.shardingsphere.proxy.backend.common.ResultList;
import io.shardingsphere.proxy.backend.common.jdbc.JDBCExecuteWorker;
import io.shardingsphere.proxy.transport.mysql.constant.ColumnType;
import io.shardingsphere.proxy.transport.mysql.packet.command.CommandResponsePackets;
import io.shardingsphere.proxy.transport.mysql.packet.generic.OKPacket;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Statement execute worker.
 *
 * @author zhangyonglun
 * @author zhaojun
 */
public final class JDBCStatementExecuteWorker extends JDBCExecuteWorker {
    
    private static final Integer FETCH_ONE_ROW_A_TIME = Integer.MIN_VALUE;
    
    private final PreparedStatement preparedStatement;
    
    private final boolean isReturnGeneratedKeys;
    
    public JDBCStatementExecuteWorker(
            final SQLType sqlType, final PreparedStatement preparedStatement, final boolean isReturnGeneratedKeys, final JDBCStatementBackendHandler jdbcStatementBackendHandler) {
        super(sqlType, jdbcStatementBackendHandler);
        this.preparedStatement = preparedStatement;
        this.isReturnGeneratedKeys = isReturnGeneratedKeys;
    }
    
    @Override
    protected CommandResponsePackets executeQueryWithStreamResultSet() throws SQLException {
        preparedStatement.setFetchSize(FETCH_ONE_ROW_A_TIME);
        return getQueryDatabaseProtocolPackets(preparedStatement.executeQuery());
    }
    
    @Override
    protected CommandResponsePackets executeQueryWithMemoryResultSet() throws SQLException {
        ResultSet resultSet = preparedStatement.executeQuery();
        ResultList resultList = new ResultList();
        while (resultSet.next()) {
            for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
                resultList.add(resultSet.getObject(i));
            }
        }
        resultList.setIterator(resultList.getResultList().iterator());
        getJdbcBackendHandler().getResultLists().add(resultList);
        return getQueryDatabaseProtocolPackets(resultSet);
    }
    
    @Override
    protected void setColumnType(final ColumnType columnType) {
        ((JDBCStatementBackendHandler) getJdbcBackendHandler()).getColumnTypes().add(columnType);
    }
    
    @Override
    protected CommandResponsePackets executeUpdate() throws SQLException {
        int affectedRows;
        long lastInsertId = 0;
        if (isReturnGeneratedKeys) {
            affectedRows = preparedStatement.executeUpdate();
            lastInsertId = getGeneratedKey(preparedStatement);
        } else {
            affectedRows = preparedStatement.executeUpdate();
        }
        return new CommandResponsePackets(new OKPacket(1, affectedRows, lastInsertId));
    }
    
    @Override
    protected CommandResponsePackets executeCommon() throws SQLException {
        boolean hasResultSet = preparedStatement.execute();
        if (hasResultSet) {
            return getCommonDatabaseProtocolPackets(preparedStatement.getResultSet());
        } else {
            return new CommandResponsePackets(new OKPacket(1, preparedStatement.getUpdateCount(), 0));
        }
    }
}
