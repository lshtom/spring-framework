package com.github.lshtom.applicationcontext.autowired;

import org.springframework.stereotype.Component;

@Component
public class Apple extends Fruit {
	private String name = "苹果";

	@Override
	public String toString() {
		return "Apple{" +
				"name='" + name + '\'' +
				'}';
	}
}
