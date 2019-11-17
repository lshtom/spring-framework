package com.github.lshtom.applicationcontext.propertyEditor;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * 通过BeanFactory后置处理器将自定义的属性编辑器注册器给添加到属性编辑器注册器列表中
 */
public class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		beanFactory.addPropertyEditorRegistrar(new DatePropertyEditorRegistry());
	}
}
