package com.github.lshtom.beanfactory.bean.factorybean;

import org.springframework.beans.factory.FactoryBean;

public class CarFactoryBean implements FactoryBean<Car> {
	private String carinfo;
	@Override
	public Car getObject() throws Exception {
		Car car = new Car();
		String[] infos = carinfo.split(",");
		car.setBrand(infos[0]);
		car.setMaxSpeed(Integer.valueOf(infos[1]));
		car.setPrice(Double.valueOf(infos[2]));
		return null;
	}

	@Override
	public Class<?> getObjectType() {
		return Car.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

	public String getCarinfo() {
		return carinfo;
	}

	public void setCarinfo(String carinfo) {
		this.carinfo = carinfo;
	}
}
