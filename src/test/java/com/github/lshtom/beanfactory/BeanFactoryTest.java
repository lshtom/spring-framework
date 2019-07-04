package com.github.lshtom.beanfactory;

import com.github.lshtom.beanfactory.bean.MyTestBean;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

@SuppressWarnings("deprecation")
public class BeanFactoryTest {

	/**
	 * 简单测试-通过BeanFactory+读取配置文件来完成Bean的加载
	 */
	@Test
	public void testSimpleLoad() {
		BeanFactory bf = new XmlBeanFactory(new ClassPathResource("com/github/lshtom/beanfactory/beanFactoryTest.xml"));
		MyTestBean bean = bf.getBean("myTestBean",MyTestBean.class);
		Assert.assertEquals("hello world", bean.getTestStr());
	}
}
