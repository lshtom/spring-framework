package com.github.lshtom.applicationcontext.autowired;

import org.springframework.stereotype.Component;

@Component
public class Fruit {
	private String name = "水果";

	@Override
	public String toString() {
		return "Fruit{" +
				"name='" + name + '\'' +
				'}';
	}
}
