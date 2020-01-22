package com.github.lshtom.enterpriseApplication.jdbc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * 直接基于JDBC的事务管理器
 */
public class DbTransactionUtils {

	/**
	 * 获取Connection
	 */
	public static Connection getConnection(DataSource dataSource) {
		try {
			return dataSource.getConnection();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * 开启事务
	 */
	public static void beginTransaction(Connection conn) {
		if (Objects.nonNull(conn)) {
			try {
				// JDBC中并没有所谓的开启事务的API，
				// 本质就是将自动提交给关闭，
				// 因为像诸如MySQL这样的会自动为任何的DML语句默认开启事务，
				// 而只要事务的提交由我们自己掌控，
				// 而不是自动提交模式下每执行一条语句就立即提交事务，
				// 那么就间接实现了事务管理（即可控制执行多条语句后再提交亦或者其他的操作）。
				if (conn.getAutoCommit()) {
					conn.setAutoCommit(false);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 回滚事务
	 */
	public static void rollBackTransaction(Connection conn) {
		if (Objects.nonNull(conn)) {
			try {
				// 非自动提交，表明将由我们自己来掌控事务
				if (!conn.getAutoCommit()) {
					// 进行事务回滚
					conn.rollback();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 提交事务
	 */
	public static void commitTransaction(Connection conn) {
		if (Objects.nonNull(conn)) {
			try {
				// 非自动提交，表明将由我们自己来掌控事务
				if (!conn.getAutoCommit()) {
					// 进行事务提交
					conn.commit();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 重置连接
	 */
	public static void resetConnectino(Connection conn) {
		try {
			if (!conn.getAutoCommit()) {
				conn.setAutoCommit(true);
				conn.close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
