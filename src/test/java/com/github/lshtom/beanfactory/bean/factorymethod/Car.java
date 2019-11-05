package com.github.lshtom.beanfactory.bean.factorymethod;

public class Car {

	private int maxSpeed;
	private String brand;
	private double price;

	private Car() {}

	public int getMaxSpeed() {
		return maxSpeed;
	}

	public void setMaxSpeed(int maxSpeed) {
		this.maxSpeed = maxSpeed;
	}

	public String getBrand() {
		return brand;
	}

	public void setBrand(String brand) {
		this.brand = brand;
	}

	public double getPrice() {
		return price;
	}

	public void setPrice(double price) {
		this.price = price;
	}

	@Override
	public String toString() {
		return "Car{" +
				"maxSpeed=" + maxSpeed +
				", brand='" + brand + '\'' +
				", price=" + price +
				'}';
	}

	public static Car getCar(int maxSpeed, String brand, double price) {
		Car car = new Car();
		car.setMaxSpeed(maxSpeed);
		car.setBrand(brand);
		car.setPrice(price);
		return car;
	}
}
