package com.github.lshtom.applicationcontext.aop;

/**
 * 用于测试：虽然当前类实现了一个有效代理接口（即可基于此接口生成代理），
 * 但是所实现的方法并不是我们要进行AOP增强的方法，
 * 所要进行AOP增强的test方法并非实现自接口，
 * 那么这种情况下Spring AOP是怎么处理呢？
 * 答案：如果是按照这样子来获取这个Bean实例的话会报错：
 * TestBean3 testBean3 = context.getBean("testBean3", TestBean3.class);
 * 报错信息：org.springframework.beans.factory.BeanNotOfRequiredTypeException: Bean named 'testBean3' is expected to be of type 'com.github.lshtom.applicationcontext.aop.TestBean3' but was actually of type 'com.sun.proxy.$Proxy26'
 * 原因：由于TestBean3中的test方法被AspectJTest中的切点命中，因此在处理TestBean3的Bean实例时，会获取到相应的增强，
 * 由于有合适的增强，那么接下来就会去创建代理类，又由于TestBean3实现了接口，且该接口是一个有效的代理接口（即JDK可利用此接口生成代理），
 * 那么Spring就会选择使用JDK代理的方式来生成代理类，也正因此，从容器中拿到的testBean3实例其实是属于TestIntf类型的，但绝不是TestBean3类型！
 *
 * 那么对于这种情况有没有解决方式呢？
 * 解决方式就是强制使用CGLib，<aop:aspectj-autoproxy proxy-target-class="true"/>
 */
public class TestBean3 implements TestIntf {
//public class TestBean3  {

	private String testStr = "testStr3";

	public String getTestStr() {
		return testStr;
	}

	public void setTestStr(String testStr) {
		this.testStr = testStr;
	}

	public void test() {
		System.out.println("test3");
	}

	@Override
	public void testIntf() {
		System.out.println("test3 implement testIntf");
	}
}
