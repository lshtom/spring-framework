package com.github.lshtom.beanfactory.bean.replaced_method;

import org.springframework.beans.factory.support.MethodReplacer;

import java.lang.reflect.Method;

public class TestMethodReplacer implements MethodReplacer {
	@Override
	public Object reimplement(Object obj, Method method, Object[] args) throws Throwable {
		System.out.println("替换掉原有的业务逻辑");
		System.out.printf("入参obj为%s,method为%s",obj, method.getName());
		return null;
	}
}
