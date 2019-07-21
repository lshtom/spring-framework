package com.github.lshtom.beanfactory.bean.lookup_method;

public abstract class GetBeanTest {
	public void showMe() {
		userBean().showMe();
	}

	public abstract User userBean();
}
