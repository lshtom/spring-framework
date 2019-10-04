package com.github.lshtom.beanfactory.bean.circular;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectFactory;

public class Test {
	public static void main(String[] args) {
		TestObjectFactoryRef ref = new TestObjectFactoryRef();
		TestA testA0 = new TestA();
		ref.addObjectFactory(new ObjectFactory<Object>() {
			@Override
			public Object getObject() throws BeansException {
				return testA0;
			}
		});
		TestA testA = (TestA) ref.getObjectFactory().getObject();
		TestA testA2 = (TestA) ref.getObjectFactory().getObject();
		System.out.println(testA);
		System.out.println(testA2);
	}
}

class Test1 {

}

class TestObjectFactoryRef {
	private ObjectFactory<?> objectFactory;

	public void addObjectFactory(ObjectFactory<?> factory) {
		this.objectFactory = factory;
	}

	public ObjectFactory<?> getObjectFactory() {
		return objectFactory;
	}
}
