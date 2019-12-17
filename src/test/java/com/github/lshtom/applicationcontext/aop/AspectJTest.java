package com.github.lshtom.applicationcontext.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;

/**
 * 定义切面
 */
@Aspect
public class AspectJTest {

	/**
	 * 定义切点
	 */
	@Pointcut("execution(* *.test(..))")
	public void test() {

	}

	/**
	 * 定义前置增强，并使用前面所定义的切点test()
	 */
	@Before("test()")
	public void beforeTest() {
		System.out.println("beforeTest");
	}

	/**
	 * 定义后置增强，并使用前面所定义的切点test()
	 */
	@After("test()")
	public void afterTset() {
		System.out.println("afterTest");
	}

	/**
	 * 定义环绕增强，并使用前面所定义的切点test()
	 */
	@Around("test()")
	public Object arountTest(ProceedingJoinPoint p) {
		System.out.println("before1");
		Object obj = null;
		try {
			obj = p.proceed();
		} catch (Throwable e) {
			e.printStackTrace();
		}
		System.out.println("after1");
		return obj;
	}
}
