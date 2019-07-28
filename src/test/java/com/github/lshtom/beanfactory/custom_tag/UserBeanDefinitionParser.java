package com.github.lshtom.beanfactory.custom_tag;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

/**
 * BeanDefinitionParser接口实现类，用来解析XSD文件中的定义和组件定义
 */
public class UserBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

	// XSD文件中Element所对应的类
	@Override
	protected Class<?> getBeanClass(Element element) {
		return  User.class;
	}

	// 从XSD文件中的Element解析并提取对应的元素
	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		String userName = element.getAttribute("userName");
		String email = element.getAttribute("email");
		// 将提取的数据放入到BeanDefinitionBuilder中，待完成所有bean的解析后统一注册到beanFactory中
		if (StringUtils.hasText(userName)) {
			builder.addPropertyValue("userName", userName);
		}
		if (StringUtils.hasText(email)) {
			builder.addPropertyValue("email", email);
		}
	}
}
