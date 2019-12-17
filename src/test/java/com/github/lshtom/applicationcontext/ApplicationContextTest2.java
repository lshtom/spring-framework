package com.github.lshtom.applicationcontext;

import com.github.lshtom.applicationcontext.aop.TestBean;
import com.github.lshtom.applicationcontext.aop.TestBean2;
import com.github.lshtom.applicationcontext.aop.TestIntf;
import com.github.lshtom.applicationcontext.aop.TestIntf2;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class ApplicationContextTest2 {

	/**
	 * 测试AOP
	 */
	@Test
	public void testAop() {
		ApplicationContext context = new ClassPathXmlApplicationContext("com/github/lshtom/applicationcontext/applicationContextTest2.xml");
		TestBean testBean = context.getBean("testBean", TestBean.class);
		TestBean2 testBean2 = context.getBean("testBean2", TestBean2.class);
		//TestBean3 testBean3 = context.getBean("testBean3", TestBean3.class);
		TestIntf testBean3 = context.getBean("testBean3", TestIntf.class);
		TestIntf2 testBean4 = context.getBean("testBean4", TestIntf2.class);

		testBean.test();
		testBean2.test233();
		//testBean3.test();
		testBean3.testIntf();
		testBean4.test();
	}
}
