package com.github.lshtom.beanfactory.bean.constructor_arg;

/**
 * 用于验证<constructor-arg>标签
 */
public class Fruit {
	private String name;
	private Double price;
	private String place;

	public Fruit(String name, Double price, String place) {
		this.name = name;
		this.price = price;
		this.place = place;
	}

	@Override
	public String toString() {
		return "Fruit{" +
				"name='" + name + '\'' +
				", price=" + price +
				", place='" + place + '\'' +
				'}';
	}
}
