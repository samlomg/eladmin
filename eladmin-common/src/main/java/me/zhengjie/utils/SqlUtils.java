/*
 *  Copyright 2019-2020 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.zhengjie.utils;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.util.StringUtils;
import com.dglbc.dbassistant.base.Express;
import com.dglbc.dbassistant.base.ParameterMode;
import com.dglbc.dbassistant.base.ProduceParameter;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.utils.enums.DataTypeEnum;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * @author /
 */
@Slf4j
public class SqlUtils {

	/**
	 * 获取数据源
	 *
	 * @param jdbcUrl /
	 * @param userName /
	 * @param password /
	 * @return DataSource
	 */
	public static DataSource getDataSource(String jdbcUrl, String userName, String password) {
		DruidDataSource druidDataSource = new DruidDataSource();
		String className;
		try {
			className = DriverManager.getDriver(jdbcUrl.trim()).getClass().getName();
		} catch (SQLException e) {
			throw new RuntimeException("Get class name error: =" + jdbcUrl);
		}
		if (StringUtils.isEmpty(className)) {
			DataTypeEnum dataTypeEnum = DataTypeEnum.urlOf(jdbcUrl);
			if (null == dataTypeEnum) {
				throw new RuntimeException("Not supported data type: jdbcUrl=" + jdbcUrl);
			}
			druidDataSource.setDriverClassName(dataTypeEnum.getDriver());
		} else {
			druidDataSource.setDriverClassName(className);
		}


		druidDataSource.setUrl(jdbcUrl);
		druidDataSource.setUsername(userName);
		druidDataSource.setPassword(password);
		// 配置获取连接等待超时的时间
		druidDataSource.setMaxWait(3000);
		// 配置初始化大小、最小、最大
		druidDataSource.setInitialSize(1);
		druidDataSource.setMinIdle(1);
		druidDataSource.setMaxActive(5);

		druidDataSource.setValidationQuery("SELECT 1");

		// 如果链接出现异常则直接判定为失败而不是一直重试
		druidDataSource.setBreakAfterAcquireFailure(true);
		try {
			druidDataSource.init();
		} catch (SQLException e) {
			log.error("Exception during pool initialization", e);
			throw new RuntimeException(e.getMessage());
		}

		return druidDataSource;
	}

	public static Connection getConnection(String jdbcUrl, String userName, String password) {
		DataSource dataSource = getDataSource(jdbcUrl, userName, password);
		Connection connection = null;
		try {
			connection = dataSource.getConnection();
		} catch (Exception ignored) {}
		try {
			int timeOut = 5;
			if (null == connection || connection.isClosed() || !connection.isValid(timeOut)) {
				log.info("connection is closed or invalid, retry get connection!");
				connection = dataSource.getConnection();
			}
		} catch (Exception e) {
			log.error("create connection error, jdbcUrl: {}", jdbcUrl);
			throw new RuntimeException("create connection error, jdbcUrl: " + jdbcUrl);
		} finally {
			CloseUtil.close(connection);
		}
		return connection;
	}

	private static void releaseConnection(Connection connection) {
		if (null != connection) {
			try {
				connection.close();
			} catch (Exception e) {
				log.error(e.getMessage(),e);
				log.error("connection close error：" + e.getMessage());
			}
		}
	}

	public static boolean testConnection(String jdbcUrl, String userName, String password) {
		Connection connection = null;
		try {
			connection = getConnection(jdbcUrl, userName, password);
			if (null != connection) {
				return true;
			}
		} catch (Exception e) {
			log.info("Get connection failed:" + e.getMessage());
		} finally {
			releaseConnection(connection);
		}
		return false;
	}

	public static String executeFile(String jdbcUrl, String userName, String password, File sqlFile) {
		Connection connection = getConnection(jdbcUrl, userName, password);
		try {
			batchExecute(connection, readSqlList(sqlFile));
		} catch (Exception e) {
			log.error("sql脚本执行发生异常:{}",e.getMessage());
			return e.getMessage();
		}finally {
			releaseConnection(connection);
		}
		return "success";
	}


	/**
	 * 批量执行sql
	 * @param connection /
	 * @param sqlList /
	 */
	public static void batchExecute(Connection connection, List<String> sqlList) {
		Statement st = null;
		try {
			st = connection.createStatement();
			for (String sql : sqlList) {
				if (sql.endsWith(";")) {
					sql = sql.substring(0, sql.length() - 1);
				}
				st.addBatch(sql);
			}
			st.executeBatch();
		} catch (SQLException throwables) {
			throwables.printStackTrace();
		} finally {
			CloseUtil.close(st);
		}
	}

	/**
	 * 将文件中的sql语句以；为单位读取到列表中
	 * @param sqlFile /
	 * @return /
	 * @throws Exception e
	 */
	private static List<String> readSqlList(File sqlFile) throws Exception {
		List<String> sqlList = Lists.newArrayList();
		StringBuilder sb = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(sqlFile), StandardCharsets.UTF_8))) {
			String tmp;
			while ((tmp = reader.readLine()) != null) {
				log.info("line:{}", tmp);
				if (tmp.endsWith(";")) {
					sb.append(tmp);
					sqlList.add(sb.toString());
					sb.delete(0, sb.length());
				} else {
					sb.append(tmp);
				}
			}
			if (!"".endsWith(sb.toString().trim())) {
				sqlList.add(sb.toString());
			}
		}

		return sqlList;
	}

	/**
	 * 查询通用
	 *
	 * @param con
	 * @param express
	 * @param row
	 * @param <T>
	 * @return List
	 */
	public static <T> List<T> query(Connection con, Express express, Function<ResultSet, T> row) {
		PreparedStatement ptm = null;
		ResultSet rs = null;
		List<T> reslut = new ArrayList<>();
		try {
			System.out.println(express.sql().toString());
			System.out.println(express.values().toString());
			ptm = con.prepareStatement(express.sql().toString());
			if (express.values() != null) {
				int param_num = express.values().size();
				for (int i = 0; i < param_num; i++) {
					ptm.setObject(i + 1, express.values().get(i));
				}
			}
			rs = ptm.executeQuery();
			while (rs.next()) {
				reslut.add(row.apply(rs));
			}
		} catch (SQLException throwables) {
			throwables.printStackTrace();
		} finally {
            releaseConnection(rs, ptm, con);
		}
		return reslut;
	}

	/**
	 * 查询单体
	 *
	 * @param con
	 * @param express
	 * @param row
	 * @param <T>
	 * @return T
	 */
	public static <T> T queryObject(Connection con, Express express, Function<ResultSet, T> row) {
		PreparedStatement ptm = null;
		ResultSet rs = null;
		T reslut = null;
		try {
			ptm = con.prepareStatement(express.sql().toString());
			if (express.values() != null) {
				int param_num = express.values().size();
				for (int i = 0; i < param_num; i++) {
					ptm.setObject(i + 1, express.values().get(i));
				}
			}
			rs = ptm.executeQuery();
			if (rs.next()) reslut = row.apply(rs);
			if (rs.next()) {
				System.err.println("错误，查询数量大于1");
			}
		} catch (SQLException throwables) {
			throwables.printStackTrace();
		} finally {
            releaseConnection(rs, ptm, con);
		}
		return reslut;
	}

	/**
	 * 更新或者插入
	 *
	 * @param con
	 * @param express
	 * @return T
	 */
	public static int update(Connection con, Express express) {
		PreparedStatement ptm = null;
		ResultSet rs = null;
		int reslut = 0;
		try {
			ptm = con.prepareStatement(express.sql().toString());
			if (express.values() != null) {
				int param_num = express.values().size();
				for (int i = 0; i < param_num; i++) {
					ptm.setObject(i + 1, express.values().get(i));
				}
			}
			reslut = ptm.executeUpdate();
		} catch (SQLException throwables) {
			throwables.printStackTrace();
		} finally {
            releaseConnection(rs, ptm, con);
		}
		return reslut;
	}

	/**
	 * 插入并返回key
	 *
	 * @param con
	 * @param express
	 * @param key
	 * @param <T>
	 * @return T
	 */
	public static <T> T insert(Connection con, Express express, Function<ResultSet, T> key) {
		PreparedStatement ptm = null;
		ResultSet rs = null;
		T reslut = null;
		try {
			ptm = con.prepareStatement(express.sql().toString());
			if (express.values() != null) {
				int param_num = express.values().size();
				for (int i = 0; i < param_num; i++) {
					ptm.setObject(i + 1, express.values().get(i));
				}
			}
			int effRows = ptm.executeUpdate();
			if (effRows == 1) {
				rs = ptm.getGeneratedKeys();
				if (rs.next()) reslut = key.apply(rs);
			}
		} catch (SQLException throwables) {
			throwables.printStackTrace();
		} finally {
            releaseConnection(rs, ptm, con);
		}
		return reslut;
	}

	/**
	 * 调用存储过程（目前只能专用，没办法能力有限，如果有好办法请来个提交）
	 *
	 * @param con
	 * @param express
	 * @param key
	 * @param <T>
	 * @return T
	 */
	public static <T> T call(Connection con, Express express, Function<CallableStatement, T> key) {
		CallableStatement cst = null;
		T ok = null;
		try {
			cst = con.prepareCall(express.sql().toString());
			cst = callParamed(cst, express.values());// 设置参数
			cst.executeUpdate();
			ok = key.apply(cst); // 获取结果
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				cst.close();
				con.close();
			} catch (SQLException throwables) {
				throwables.printStackTrace();
			}
		}
		return ok;
	}

	/**
	 * 设置存储过程的参数
	 *
	 * @param cst
	 * @param produceParameters
	 * @return
	 */
	public static CallableStatement callParamed(CallableStatement cst, List<Object> produceParameters) {
		try {
			for (Object pp : produceParameters) {
				ProduceParameter pr = (ProduceParameter) pp;
				if (pr.mode().equals(ParameterMode.IN)) {
					cst.setObject(pr.num(), pr.value());
				} else if (pr.mode().equals(ParameterMode.INOUT)) {
					cst.setObject(pr.num(), pr.value());
				} else if (pr.mode().equals(ParameterMode.OUT)) {
					cst.registerOutParameter(pr.num(), pr.types());
				}
			}
		} catch (SQLException throwables) {
			throwables.printStackTrace();
		}
		return cst;
	}



	/**
	 * 释放资源
	 */
	public static void releaseConnection(Statement stmt, Connection conn) {
        releaseConnection(null, stmt, conn);
	}

	/**
	 * 释放资源
	 */
	public static void releaseConnection(ResultSet rs, Statement stmt, Connection conn) {
		if (rs != null) {
			try {
				rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		if (stmt != null) {
			try {
				stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		if (conn != null) {
			try {
				conn.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
}
