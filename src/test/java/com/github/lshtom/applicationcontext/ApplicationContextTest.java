package com.github.lshtom.applicationcontext;

import com.github.lshtom.applicationcontext.autowired.Config;
import com.github.lshtom.applicationcontext.autowired.Fruit;
import com.github.lshtom.applicationcontext.autowired.Person;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class ApplicationContextTest {

	/**
	 * 测试@Autowire注解
	 */
	@Test
	public void testAutowiredByType() {
		ApplicationContext context = new AnnotationConfigApplicationContext(Config.class);
		Person person = context.getBean(Person.class);
		System.out.println(person);
	}

	/**
	 * 测试按照类型获取Bean时，若有多于两个符合条件的Bean时，情况如何？
	 * 测试结果是：回报NoUniqueBeanDefinitionException
	 */
	@Test
	public void testGetBeanByTypeWhenHaveMoreThenOneMatch() {
		ApplicationContext context = new AnnotationConfigApplicationContext(Config.class);
		Fruit fruit = context.getBean(Fruit.class);
		System.out.println(fruit);
	}
}
