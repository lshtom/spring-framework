package com.github.lshtom.beanfactory;

import com.github.lshtom.beanfactory.bean.circular.TestA;
import com.github.lshtom.beanfactory.bean.factorybean.Car;
import com.github.lshtom.beanfactory.bean.instantiation_aware_bean_post_processor.TestBean;
import org.junit.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

@SuppressWarnings("deprecation")
public class BeanFactoryTest2 {

	/**
	 * 测试FactoryBean
	 */
	@Test
	public void testFactoryBean() {
		BeanFactory bf = new XmlBeanFactory(new ClassPathResource("com/github/lshtom/beanfactory/beanFactoryTest2.xml"));
		Car car = bf.getBean("car", Car.class);
		System.out.println(car);
	}

	@Test
	public void testCircularDependency() {
		BeanFactory bf = new XmlBeanFactory(new ClassPathResource("com/github/lshtom/beanfactory/beanFactoryTest2.xml"));
		Object obj = bf.getBean("testA");
		System.out.println(obj);
	}

	/**
	 * 测试显式依赖depends-on
	 * 说明：在BeanA上使用depends-on指定BeanB，
	 * 其意义是告诉Spring容器，在BeanA创建初始化之前，必须先完成BeanB的创建初始化。
	 * 但这个与属性注入/构造器注入没啥关系，即使BeanA中有一个属性是BeanB，
	 * Spring也不会因指定了depends-on而帮你注入
	 */
	@Test
	public void testDependsOn() {
		BeanFactory bf = new XmlBeanFactory(new ClassPathResource("com/github/lshtom/beanfactory/beanFactoryTest2.xml"));
		TestA testA = bf.getBean(TestA.class);
		System.out.println(testA);
	}

	/**
	 * 测试后置处理器InstantiationAwareBeanPostProcessor
	 * 及属性填充
	 * Tips:在XML配置文件中，只有显示的使用autowire="byType"，才会触发进入的autowireByType的方法中，
	 * 同样的，也只有显示的使用autowire="byName"，才会触发进入到autowireByName的方法中.
	 */
	@Test
	public void testInstantiationAwareBeanPostProcessor() {
		BeanFactory bf = new XmlBeanFactory(new ClassPathResource("com/github/lshtom/beanfactory/beanFactoryTest3.xml"));
		//((XmlBeanFactory) bf).addBeanPostProcessor(new BeanPostProcessor());
		TestBean testBean = bf.getBean(TestBean.class);
		System.out.println(testBean);
	}

	/**
	 * 测试工厂方法的使用
	 */
	@Test
	public void testFactoryMethod() {
		BeanFactory bf = new XmlBeanFactory(new ClassPathResource("com/github/lshtom/beanfactory/beanFactoryTest3.xml"));
		Object car =  bf.getBean("myCar");
		System.out.println(car);
	}
}
