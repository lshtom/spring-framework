package com.github.lshtom.beanfactory.bean.outer_innter;

/**
 * 外部Bean-内部Bean测试：作为内部Bean
 */
public class Address {

	private String country;
	private String province;
	private String city;
	private String detail;

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getProvince() {
		return province;
	}

	public void setProvince(String province) {
		this.province = province;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getDetail() {
		return detail;
	}

	public void setDetail(String detail) {
		this.detail = detail;
	}

	@Override
	public String toString() {
		return "Address{" +
				"country='" + country + '\'' +
				", province='" + province + '\'' +
				", city='" + city + '\'' +
				", detail='" + detail + '\'' +
				'}';
	}
}
