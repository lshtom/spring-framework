package com.github.lshtom.enterpriseApplication.jdbc.service;

import com.github.lshtom.enterpriseApplication.jdbc.model.User;

import java.util.List;

public interface UserService {

	void save(User user);

	List<User> getUsers();

	List<User> getUsersByName(String name);

	String getUserNameById(Integer id);
}
