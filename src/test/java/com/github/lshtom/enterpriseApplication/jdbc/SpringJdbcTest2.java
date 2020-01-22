package com.github.lshtom.enterpriseApplication.jdbc;

import com.github.lshtom.enterpriseApplication.jdbc.model.User;
import com.github.lshtom.enterpriseApplication.jdbc.service.UserService;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.support.JdbcUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 测试JDBC下事务的使用
 */
public class SpringJdbcTest2 {

	/**
	 * 测试Spring的事务管理功能
	 */
	@Test
	public void testTransaction() {
		ApplicationContext context = new ClassPathXmlApplicationContext("com/github/lshtom/enterpriseApplication/jdbc/jdbcTest2.xml");
		UserService userService = context.getBean("userService", UserService.class);
		User user = new User();
		user.setName("老何");
		user.setAge(27);
		user.setSex("男");
		userService.saveWithTransaction(user);
		userService.saveWithTransaction(user);
	}

	/**
	 * 测试直接利用JDBC的API来实现事务管理
	 */
	@Test
	public void testJDBCTransaction() {
		ApplicationContext context = new ClassPathXmlApplicationContext("com/github/lshtom/enterpriseApplication/jdbc/jdbcTest3.xml");
		UserService userService = context.getBean("userService", UserService.class);
		User user = new User();
		user.setName("老何");
		user.setAge(27);
		user.setSex("男");
		// 1、获取数据源
		BasicDataSource dataSource = context.getBean("dataSoruce", BasicDataSource.class);
		// ！！！对于dbcp2数据源，一定要将这个设置为false，否则直接设置Connection的AutoCommit为false其实不起作用
//		dataSource.setDefaultAutoCommit(false);
		// 2、获取连接对象实例
		Connection conn = DbTransactionUtils.getConnection(dataSource);
		// 3、开启事务
		DbTransactionUtils.beginTransaction(conn);
		// 4、执行目标方法
		try {
			userService.saveWithTransaction(user);
		} catch (Throwable e) {
			// 5、事务回滚
			DbTransactionUtils.rollBackTransaction(conn);
			DbTransactionUtils.resetConnectino(conn);
			// 事务回滚后一定要抛出异常，否则后面又接着执行事务提交，会报错
			throw e;
		}
		// 6、事务提交
		DbTransactionUtils.commitTransaction(conn);
		DbTransactionUtils.resetConnectino(conn);
	}

	/**
	 * 测试直接利用JDBC的API来实现事务管理（同时数据库操作也是使用原生的JDBC操作）
	 */
	@Test
	public void testJDBCTransaction2() throws Exception {
		// 1）类加载器加载数据库驱动
		Class.forName("com.mysql.cj.jdbc.Driver");
		// 2）创建数据库连接对象
		Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/example2?serverTimezone=UTC", "root", "admin");
		// 3) 创建Statement对象
		Statement stm = conn.createStatement();
		// 4）开启事务
		DbTransactionUtils.beginTransaction(conn);
		try {
			// 5）执行SQL
			doSomething(stm);
		} catch (Throwable e) {
			// 6）事务回滚
			DbTransactionUtils.rollBackTransaction(conn);
			stm.close();
			DbTransactionUtils.resetConnectino(conn);
			throw e;
		}
		// 7）提交事务
		DbTransactionUtils.commitTransaction(conn);
		stm.close();
		DbTransactionUtils.resetConnectino(conn);
	}

	@Test
	public void testJDBCTransaction3() throws Exception {
		// 1）获取数据源
		DataSource dataSource = getDataSource("com.mysql.cj.jdbc.Driver",
				"jdbc:mysql://localhost:3306/example2?serverTimezone=UTC",
				"root", "admin");
		// 2）创建数据库连接对象
		Connection conn = dataSource.getConnection();
		// 3) 创建Statement对象
		// 也可以用conn.prepareStatement()来创建PreparedStatement对象，然后使用PreparedStatement进行后续操作
		Statement stm = conn.createStatement();
		// 4）开启事务
		DbTransactionUtils.beginTransaction(conn);
		try {
			// 5）执行SQL
			doSomething(stm);
		} catch (Throwable e) {
			// 6）事务回滚
			DbTransactionUtils.rollBackTransaction(conn);
			stm.close();
			DbTransactionUtils.resetConnectino(conn);
			throw e;
		}
		// 7）提交事务
		DbTransactionUtils.commitTransaction(conn);
		stm.close();
		DbTransactionUtils.resetConnectino(conn);
	}

	private DataSource getDataSource(String driverClassName, String url, String username, String password) {
		// 使用dbcp2数据源
		BasicDataSource dataSource = new BasicDataSource();
		dataSource.setDriverClassName(driverClassName);
		dataSource.setUrl(url);
		dataSource.setUsername(username);
		dataSource.setPassword(password);
		dataSource.setDefaultAutoCommit(true);
		return dataSource;
	}

	private void doSomething(Statement stm) throws Exception {
		stm.executeUpdate("INSERT INTO user(name,age,sex) VALUES('李红',24,'女')");
		// 模拟异常
		int aa = 1 /0;
	}
}
