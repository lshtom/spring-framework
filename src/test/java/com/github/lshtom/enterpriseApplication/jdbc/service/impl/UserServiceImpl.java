package com.github.lshtom.enterpriseApplication.jdbc.service.impl;

import com.github.lshtom.enterpriseApplication.jdbc.mapper.UserRowMapper;
import com.github.lshtom.enterpriseApplication.jdbc.model.User;
import com.github.lshtom.enterpriseApplication.jdbc.service.UserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Types;
import java.util.List;

public class UserServiceImpl implements UserService {

	private JdbcTemplate jdbcTemplate;

	public void setDataSource(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void saveWithTransaction(User user) {
		jdbcTemplate.update("INSERT INTO user(name,age,sex) VALUES(?,?,?)",
				new Object[]{user.getName(), user.getAge(), user.getSex()},
				new int[]{Types.VARCHAR, Types.INTEGER, Types.VARCHAR});
		//throw new RuntimeException("测试事务");
//		int a = 5 / 0;
	}

	@Override
	public void save(User user) {
		jdbcTemplate.update("INSERT INTO user(name,age,sex) VALUES(?,?,?)",
				new Object[]{user.getName(), user.getAge(), user.getSex()},
				new int[]{Types.VARCHAR, Types.INTEGER, Types.VARCHAR});
	}

	@Override
	public List<User> getUsers() {
		// 查询没有参数：
		List<User> list = jdbcTemplate.query("SELECT * FROM user", new UserRowMapper());
		return list;
	}

	@Override
	public List<User> getUsersByName(String name) {
		// 查询有入参：
		List<User> list = jdbcTemplate.query("SELECT * FROM user WHERE name = ?", new Object[]{name}, new int[]{Types.VARCHAR}, new UserRowMapper());
		return list;
	}

	@Override
	public String getUserNameById(Integer id) {
		String userName = jdbcTemplate.queryForObject("SELECT name FROM user WHERE id = ?", new Object[]{id}, new int[]{Types.INTEGER}, String.class);
		return userName;
	}
}
