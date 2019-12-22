package com.github.lshtom.enterpriseApplication.jdbc.mapper;

import com.github.lshtom.enterpriseApplication.jdbc.model.User;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class UserRowMapper implements RowMapper {

	@Override
	public Object mapRow(ResultSet rs, int rowNum) throws SQLException {
		User user = new User();
		user.setId(rs.getInt("id"));
		user.setName(rs.getString("name"));
		user.setAge(rs.getInt("age"));
		user.setSex(rs.getString("sex"));
		return user;
	}
}
