package com.github.lshtom.beanfactory;

import com.github.lshtom.beanfactory.bean.MyTestBean;
import com.github.lshtom.beanfactory.bean.MyTestBean2;
import com.github.lshtom.beanfactory.bean.constructor_arg.Fruit;
import com.github.lshtom.beanfactory.bean.lookup_method.GetBeanTest;
import com.github.lshtom.beanfactory.bean.outer_innter.Person;
import com.github.lshtom.beanfactory.bean.replaced_method.TestChangeMethod;
import com.github.lshtom.beanfactory.custom_tag.User;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.xml.DefaultNamespaceHandlerResolver;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

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

	/**
	 * 测试Resource接口可重复读取
	 *
	 * @throws IOException
	 */
	@Test
	public void testRepeatReadResource() throws IOException {
		Resource resource = new ClassPathResource("com/github/lshtom/beanfactory/beanFactoryTest.xml");
		// 第一次获取资源并进行读取
		System.out.println("第一次对文件进行读取：");
		InputStream is1 =  resource.getInputStream();
		BufferedReader br1 = new BufferedReader(new InputStreamReader(is1));
		String curLine;
		while ((curLine = br1.readLine()) != null) {
			System.out.println(curLine);
		}
		System.out.println("=================================");

		// 第二次获取资源并进行读取
		System.out.println("第二次对文件进行读取");
		InputStream is2 = resource.getInputStream();
		BufferedReader br2 = new BufferedReader(new InputStreamReader(is2));
		while ((curLine = br2.readLine()) != null) {
			System.out.println(curLine);
		}
	}

	/**
	 * 测试外部Bean-内部Bean
	 */
	@Test
	public void testOuterAndInnterBean() {
		BeanFactory bf = new XmlBeanFactory(new ClassPathResource("com/github/lshtom/beanfactory/beanFactoryTest.xml"));
		Person person = bf.getBean("person", Person.class);
		System.out.println(person);
	}

	/**
	 * 测试Autowired属性及Autowire-Candidate属性的使用
	 */
	@Test
	public void testAutowiredAndAutowiredCandidateProperty() {
		BeanFactory bf = new XmlBeanFactory(new ClassPathResource("com/github/lshtom/beanfactory/beanFactoryTest.xml"));
		MyTestBean2 testBean2 = bf.getBean(MyTestBean2.class);
		Assert.assertNotNull(testBean2);
		Assert.assertNotNull(testBean2.getBean());
	}

	/**
	 * 测试lookup-method标签
	 * 说明：lookup-method标签的作用是可实现获取器注入
	 */
	@Test
	public void testLookupMethod() {
		BeanFactory bf = new XmlBeanFactory(new ClassPathResource("com/github/lshtom/beanfactory/beanFactoryTest.xml"));
		GetBeanTest getBeanTest = bf.getBean("getBeanTest", GetBeanTest.class);
		getBeanTest.showMe();
	}

	/**
	 * 测试replaced-method标签
	 */
	@Test
	public void testReplacedMethod() {
		BeanFactory bf = new XmlBeanFactory(new ClassPathResource("com/github/lshtom/beanfactory/beanFactoryTest.xml"));
		TestChangeMethod testChangeMethod = bf.getBean("testChangeMethod", TestChangeMethod.class);
		testChangeMethod.changeMe();
	}

	/**
	 * 测试constructor-arg标签
	 */
	@Test
	public void testConstructorArg() {
		BeanFactory bf = new XmlBeanFactory(new ClassPathResource("com/github/lshtom/beanfactory/beanFactoryTest.xml"));
		Fruit fruit = (Fruit)bf.getBean("fruit", Fruit.class);
		System.out.println(fruit);
	}

	/**
	 * 测试自定义标签
	 */
	@Test
	public void testCustomTag() {
		ApplicationContext bf = new ClassPathXmlApplicationContext("com/github/lshtom/beanfactory/beanFactoryTest.xml");
		User user = bf.getBean("testCustomTag", User.class);
		System.out.println(user.getUserName() + "," + user.getEmail());
	}

	/**
	 * 测试扫描.handlers文件获取handler信息
	 *
	 * @throws Exception
	 */
	@Test
	public void testNamespaceHandlerScan() throws Exception {
		String location = DefaultNamespaceHandlerResolver.DEFAULT_HANDLER_MAPPINGS_LOCATION;
		Properties mappings = PropertiesLoaderUtils.loadAllProperties(location);
		System.out.println(mappings);
	}
}
