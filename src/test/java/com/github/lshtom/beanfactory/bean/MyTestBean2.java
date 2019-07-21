package com.github.lshtom.beanfactory.bean;

public class MyTestBean2 {

	private String name = "hello world";
	private MyTestBean3 bean;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public MyTestBean3 getBean() {
		return bean;
	}

	public void setBean(MyTestBean3 bean) {
		this.bean = bean;
	}
}
