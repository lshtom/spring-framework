package com.github.lshtom.beanfactory.bean.outer_innter;

/**
 * 外部Bean-内部Bean测试：作为外部Bean
 */
public class Person {

	private String name;
	private int age;
	private String phoneNumber;
	private Address address;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getAge() {
		return age;
	}

	public void setAge(int age) {
		this.age = age;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public Address getAddress() {
		return address;
	}

	public void setAddress(Address address) {
		this.address = address;
	}

	@Override
	public String toString() {
		return "Person{" +
				"name='" + name + '\'' +
				", age=" + age +
				", phoneNumber='" + phoneNumber + '\'' +
				", address=" + address +
				'}';
	}
}
