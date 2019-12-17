/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.aop.framework;

import org.springframework.aop.SpringProxy;

import java.io.Serializable;
import java.lang.reflect.Proxy;

/**
 * Default {@link AopProxyFactory} implementation, creating either a CGLIB proxy
 * or a JDK dynamic proxy.
 *
 * <p>Creates a CGLIB proxy if one the following is true for a given
 * {@link AdvisedSupport} instance:
 * <ul>
 * <li>the {@code optimize} flag is set
 * <li>the {@code proxyTargetClass} flag is set
 * <li>no proxy interfaces have been specified
 * </ul>
 *
 * <p>In general, specify {@code proxyTargetClass} to enforce a CGLIB proxy,
 * or specify one or more interfaces to use a JDK dynamic proxy.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 12.03.2004
 * @see AdvisedSupport#setOptimize
 * @see AdvisedSupport#setProxyTargetClass
 * @see AdvisedSupport#setInterfaces
 */
@SuppressWarnings("serial")
public class DefaultAopProxyFactory implements AopProxyFactory, Serializable {

	@Override
	public AopProxy createAopProxy(AdvisedSupport config) throws AopConfigException {
		// 采用激进优化策略 或 设置为使用CGLib 或 目标类中没有实现有效的接口（可被用于生成代理的，且不是容器回调接口和语言内置接口）都将尝试采用CGLib来生成代理类
		if (config.isOptimize() || config.isProxyTargetClass() || hasNoUserSuppliedProxyInterfaces(config)) {
			Class<?> targetClass = config.getTargetClass();
			if (targetClass == null) {
				throw new AopConfigException("TargetSource cannot determine target class: " +
						"Either an interface or a target is required for proxy creation.");
			}
			// 对于目标类为接口或目标类为Proxy代理类的特殊情况，还是采用JDK动态代理
			if (targetClass.isInterface() || Proxy.isProxyClass(targetClass)) {
				// Tips:config做为构造器入参传入，JdkDynamicAopProxy类中可利用该参数来获取到所有的增强器以及一些全局设置（proxyTargetClass、optimize等）,
				// 下面的ObjenesisCglibAopProxy的构造器入参为config也是这个道理！！！
				return new JdkDynamicAopProxy(config);
			}
			// 采用CGLib生成代理
			return new ObjenesisCglibAopProxy(config);
		}
		// 对于不是上面三种情况的则采用JDK动态代理
		else {
			return new JdkDynamicAopProxy(config);
		}

		// Tips：不管是JdkDynamicAopProxy还是ObjenesisCglibAopProxy类，
		// 其均实现了AopProxy接口，所以当前的createAopProxy方法返回的是AopProxy接口，
		// 这样子的话，对于后续的使用者而言无需关心到具体的实现，这也是面向接口编程的一种体现！
	}

	/**
	 * Determine whether the supplied {@link AdvisedSupport} has only the
	 * {@link org.springframework.aop.SpringProxy} interface specified
	 * (or no proxy interfaces specified at all).
	 */
	private boolean hasNoUserSuppliedProxyInterfaces(AdvisedSupport config) {
		// 获取目标Bean中所实现的所有接口的类型
		Class<?>[] ifcs = config.getProxiedInterfaces();
		// 如果没有实现接口或者只实现了一个SpringProxy接口的话，都返回false，即不能利用JDK动态代理
		return (ifcs.length == 0 || (ifcs.length == 1 && SpringProxy.class.isAssignableFrom(ifcs[0])));
	}

}
