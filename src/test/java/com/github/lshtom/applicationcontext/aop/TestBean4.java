package com.github.lshtom.applicationcontext.aop;

public class TestBean4 implements TestIntf2 {
//public class TestBean3  {

	private String testStr = "testStr4";

	public String getTestStr() {
		return testStr;
	}

	public void setTestStr(String testStr) {
		this.testStr = testStr;
	}

	@Override
	public void test() {
		System.out.println("test4-implement TestIntf2");
	}
}
