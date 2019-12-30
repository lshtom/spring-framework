package com.github.lshtom.enterpriseApplication.mybatis.mapper;

import com.github.lshtom.enterpriseApplication.mybatis.model.User;

/**
 * Mapper接口
 */
public interface UserMapper {
	void insertUser(User user);
	User getUser(Integer id);
}
