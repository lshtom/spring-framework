package com.github.lshtom.beanfactory.custom_tag;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

/**
 * 注册解析器
 * 注意：其实解析器并非注册到Spring容器中，而仅仅是将解析器实例缓存到NamespaceHandlerSupport的parse实例变量中（Map类型）
 */
public class MyNamespaceHandler extends NamespaceHandlerSupport {
	@Override
	public void init() {
		registerBeanDefinitionParser("user", new UserBeanDefinitionParser());
	}
}
