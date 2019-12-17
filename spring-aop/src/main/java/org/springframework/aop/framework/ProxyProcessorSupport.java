/*
 * Copyright 2002-2018 the original author or authors.
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

import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

import java.io.Closeable;

/**
 * Base class with common functionality for proxy processors, in particular
 * ClassLoader management and the {@link #evaluateProxyInterfaces} algorithm.
 *
 * @author Juergen Hoeller
 * @since 4.1
 * @see AbstractAdvisingBeanPostProcessor
 * @see org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator
 */
@SuppressWarnings("serial")
public class ProxyProcessorSupport extends ProxyConfig implements Ordered, BeanClassLoaderAware, AopInfrastructureBean {

	/**
	 * This should run after all other processors, so that it can just add
	 * an advisor to existing proxies rather than double-proxy.
	 */
	private int order = Ordered.LOWEST_PRECEDENCE;

	@Nullable
	private ClassLoader proxyClassLoader = ClassUtils.getDefaultClassLoader();

	private boolean classLoaderConfigured = false;


	/**
	 * Set the ordering which will apply to this processor's implementation
	 * of {@link Ordered}, used when applying multiple processors.
	 * <p>The default value is {@code Ordered.LOWEST_PRECEDENCE}, meaning non-ordered.
	 * @param order the ordering value
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	/**
	 * Set the ClassLoader to generate the proxy class in.
	 * <p>Default is the bean ClassLoader, i.e. the ClassLoader used by the containing
	 * {@link org.springframework.beans.factory.BeanFactory} for loading all bean classes.
	 * This can be overridden here for specific proxies.
	 */
	public void setProxyClassLoader(@Nullable ClassLoader classLoader) {
		this.proxyClassLoader = classLoader;
		this.classLoaderConfigured = (classLoader != null);
	}

	/**
	 * Return the configured proxy ClassLoader for this processor.
	 */
	@Nullable
	protected ClassLoader getProxyClassLoader() {
		return this.proxyClassLoader;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		if (!this.classLoaderConfigured) {
			this.proxyClassLoader = classLoader;
		}
	}


	/**
	 * Check the interfaces on the given bean class and apply them to the {@link ProxyFactory},
	 * if appropriate.
	 * <p>Calls {@link #isConfigurationCallbackInterface} and {@link #isInternalLanguageInterface}
	 * to filter for reasonable proxy interfaces, falling back to a target-class proxy otherwise.
	 * @param beanClass the class of the bean
	 * @param proxyFactory the ProxyFactory for the bean
	 */
	protected void evaluateProxyInterfaces(Class<?> beanClass, ProxyFactory proxyFactory) {
		// 【步骤1】：先获取目标类所实现的所有接口
		Class<?>[] targetInterfaces = ClassUtils.getAllInterfacesForClass(beanClass, getProxyClassLoader());
		// 标志变量hasReasonableProxyInterface：是否由合理的代理接口（意思是可根据此接口来生成代理）
		boolean hasReasonableProxyInterface = false;
		// 【步骤2】：依次遍历所有的接口，判断是否有适合用于生成代理的接口
		for (Class<?> ifc : targetInterfaces) {
			// isConfigurationCallbackInterface方法用于判断是否为容器回调类接口（如InitializingBean、Aware、Closeable等）
			// isInternalLanguageInterface方法用于判断是否为内部语言接口（如groovy.lang.GroovyObject、.cglib.proxy.Factory等）
			if (!isConfigurationCallbackInterface(ifc) && !isInternalLanguageInterface(ifc) &&
					ifc.getMethods().length > 0) {
				hasReasonableProxyInterface = true;
				break;
			}
		}
		// 【步骤3】：如果找到适合生成代理的接口，那么将目标类所实现的所有的接口到加入（毕竟生成的代理类也是要实现目标类的所有接口的），
		// 如果没有找到合适的接口，则设置为基于类进行代理
		if (hasReasonableProxyInterface) {
			// Must allow for introductions; can't just set interfaces to the target's interfaces only.
			for (Class<?> ifc : targetInterfaces) {
				proxyFactory.addInterface(ifc);
			}
		}
		else {
			proxyFactory.setProxyTargetClass(true);
		}

		// Tips：通过分析这个evaluateProxyInterfaces方法时，可以明白：
		// 在开启了AOP的功能后，如果当前类中的某个方法被某个切面类的切点所命中，并且有相适配的增强，
		// 那么Spring会为当前类生成代理，如果恰好当前类有实现了某个有效接口（非容器回调接口、语言内置接口等）,
		// Spring会选择采用JDK代理，根据接口来生成代理类，
		// 但是如果这个被切点所命中的方法并不是在接口中的话，那就会有问题了，所生成的代理类时不会含有这个方法的！！！
		// 解决方式就是强制使用CGLib代理。
	}

	/**
	 * Determine whether the given interface is just a container callback and
	 * therefore not to be considered as a reasonable proxy interface.
	 * <p>If no reasonable proxy interface is found for a given bean, it will get
	 * proxied with its full target class, assuming that as the user's intention.
	 * @param ifc the interface to check
	 * @return whether the given interface is just a container callback
	 */
	protected boolean isConfigurationCallbackInterface(Class<?> ifc) {
		return (InitializingBean.class == ifc || DisposableBean.class == ifc || Closeable.class == ifc ||
				AutoCloseable.class == ifc || ObjectUtils.containsElement(ifc.getInterfaces(), Aware.class));
	}

	/**
	 * Determine whether the given interface is a well-known internal language interface
	 * and therefore not to be considered as a reasonable proxy interface.
	 * <p>If no reasonable proxy interface is found for a given bean, it will get
	 * proxied with its full target class, assuming that as the user's intention.
	 * @param ifc the interface to check
	 * @return whether the given interface is an internal language interface
	 */
	protected boolean isInternalLanguageInterface(Class<?> ifc) {
		return (ifc.getName().equals("groovy.lang.GroovyObject") ||
				ifc.getName().endsWith(".cglib.proxy.Factory") ||
				ifc.getName().endsWith(".bytebuddy.MockAccess"));
	}

}
