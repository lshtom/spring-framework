package com.github.lshtom.beanfactory.custom_tag;

/**
 * POJO类，主要用于接收配置文件的配置信息
 */
public class User {

	private String userName;
	private String email;

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}
}
