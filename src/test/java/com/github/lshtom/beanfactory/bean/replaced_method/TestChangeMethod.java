package com.github.lshtom.beanfactory.bean.replaced_method;

/**
 * 用来测试replaced-method标签
 */
public class TestChangeMethod {

	/**
	 * 这个方法可看作是原来的业务逻辑
	 * 说明：必须得是public方法才可以被替换
	 */
	public void changeMe() {
		System.out.println("原有的业务逻辑");
	}
}
