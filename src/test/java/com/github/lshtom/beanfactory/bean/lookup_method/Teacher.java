package com.github.lshtom.beanfactory.bean.lookup_method;

public class Teacher extends User {
	@Override
	public void showMe() {
		System.out.println("I'm a teacher");
	}
}
