package com.github.lshtom.applicationcontext.autowired;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Person {

	@Autowired
	private Address address;

	@Override
	public String toString() {
		return "Person{" +
				"address=" + address +
				'}';
	}
}
