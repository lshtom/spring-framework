package com.github.lshtom.applicationcontext.autowired;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class Person {

	@Autowired
	private Address address;

	@Autowired
	private ApplicationContextAware contextAware;

	@Override
	public String toString() {
		return "Person{" +
				"address=" + address +
				'}';
	}
}
