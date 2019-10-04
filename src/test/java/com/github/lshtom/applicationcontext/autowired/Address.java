package com.github.lshtom.applicationcontext.autowired;

import org.springframework.stereotype.Component;

@Component
public class Address {

	private String country = "中国";
	private String city = "大连";
	private String area;

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getArea() {
		return area;
	}

	public void setArea(String area) {
		this.area = area;
	}

	@Override
	public String toString() {
		return "Address{" +
				"country='" + country + '\'' +
				", city='" + city + '\'' +
				", area='" + area + '\'' +
				'}';
	}
}
