package com.github.lshtom.beanfactory.bean.instantiation_aware_bean_post_processor;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;

public class BeanPostProcessor implements InstantiationAwareBeanPostProcessor {
	@Override
	public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
		if (beanClass.equals(TestBean.class)) {
			return new TestBean();
		}
		return null;
	}
}
