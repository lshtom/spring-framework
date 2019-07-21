package com.github.lshtom.beanfactory.bean.lookup_method;

public class Student extends User {
	@Override
	public void showMe() {
		System.out.println("I'm a student");
	}
}
