package com.github.lshtom.beanfactory.bean.instantiation_aware_bean_post_processor;

public class TestBean {

	private String name;
	private String address;
	private int age;
	private TestBean2 testBean2;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public int getAge() {
		return age;
	}

	public void setAge(int age) {
		this.age = age;
	}

	public TestBean2 getTestBean2() {
		return testBean2;
	}

	public void setTestBean2(TestBean2 testBean2) {
		this.testBean2 = testBean2;
	}
}
