package com.github.lshtom.enterpriseApplication.jdbc;

import com.github.lshtom.enterpriseApplication.jdbc.model.User;
import com.github.lshtom.enterpriseApplication.jdbc.service.UserService;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

public class SpringJdbcTest {

	/**
	 * 测试原生的JDBC写法
	 */
	@Test
	public void testJdbc() throws Exception {
		// 1）类加载器加载数据库驱动
		Class.forName("com.mysql.cj.jdbc.Driver");
		// 2）创建数据库连接对象
		Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/example2?serverTimezone=UTC", "root", "admin");
		// 3) 创建Statement对象
		Statement stm = conn.createStatement();
		// 4）执行SQL
		stm.executeUpdate("INSERT INTO user(name,age,sex) VALUES('李四',24,'男')");
		// 5）关闭连接
		stm.close();
		conn.close();
	}

	/**
	 * 测试使用指定数据源的JDBC写法
	 */
	@Test
	public void testJdbcWithDataSource() throws Exception {
		// 1）获取数据源
		DataSource dataSource = getDataSource("com.mysql.cj.jdbc.Driver",
				"jdbc:mysql://localhost:3306/example2?serverTimezone=UTC",
				"root", "admin");
		// 2）创建数据库连接对象
		Connection conn = dataSource.getConnection();
		// 3) 创建Statement对象
		// 也可以用conn.prepareStatement()来创建PreparedStatement对象，然后使用PreparedStatement进行后续操作
		Statement stm = conn.createStatement();
		// 4）执行SQL
		stm.executeUpdate("INSERT INTO user(name,age,sex) VALUES('王五',27,'男')");
		// 5）关闭连接
		stm.close();
		conn.close();
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

	/**
	 * 测试Spring JdbcTemplate
	 */
	@Test
	public void testJdbcTemplate() {
		ApplicationContext context = new ClassPathXmlApplicationContext("com/github/lshtom/enterpriseApplication/jdbc/jdbcTest.xml");
		UserService userService = context.getBean("userService", UserService.class);
		User user = new User();
		user.setName("张三");
		user.setAge(29);
		user.setSex("男");
		userService.save(user);

		List<User> users = userService.getUsers();
		System.out.println("====得到所有的User：====");
		for (User u : users) {
			System.out.println(u);
		}
	}

	/**
	 * 测试有参数的Query方法查询
	 */
	@Test
	public void testJdbcTemplateQuery() {
		ApplicationContext context = new ClassPathXmlApplicationContext("com/github/lshtom/enterpriseApplication/jdbc/jdbcTest.xml");
		UserService userService = context.getBean("userService", UserService.class);
		List<User> users = userService.getUsersByName("王五");
		System.out.println(users);
	}

	/**
	 * 测试有QueryForObject方法
	 */
	@Test
	public void testQueryForObject() {
		ApplicationContext context = new ClassPathXmlApplicationContext("com/github/lshtom/enterpriseApplication/jdbc/jdbcTest.xml");
		UserService userService = context.getBean("userService", UserService.class);
		String userName = userService.getUserNameById(2);
		System.out.println(userName);
	}
}
