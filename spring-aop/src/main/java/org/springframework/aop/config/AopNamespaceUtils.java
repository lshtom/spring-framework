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

package org.springframework.aop.config;

import org.w3c.dom.Element;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.lang.Nullable;

/**
 * Utility class for handling registration of auto-proxy creators used internally
 * by the '{@code aop}' namespace tags.
 *
 * <p>Only a single auto-proxy creator should be registered and multiple configuration
 * elements may wish to register different concrete implementations. As such this class
 * delegates to {@link AopConfigUtils} which provides a simple escalation protocol.
 * Callers may request a particular auto-proxy creator and know that creator,
 * <i>or a more capable variant thereof</i>, will be registered as a post-processor.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @since 2.0
 * @see AopConfigUtils
 */
public abstract class AopNamespaceUtils {

	/**
	 * The {@code proxy-target-class} attribute as found on AOP-related XML tags.
	 */
	public static final String PROXY_TARGET_CLASS_ATTRIBUTE = "proxy-target-class";

	/**
	 * The {@code expose-proxy} attribute as found on AOP-related XML tags.
	 */
	private static final String EXPOSE_PROXY_ATTRIBUTE = "expose-proxy";


	public static void registerAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {

		BeanDefinition beanDefinition = AopConfigUtils.registerAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
		registerComponentIfNecessary(beanDefinition, parserContext);
	}

	public static void registerAspectJAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {

		BeanDefinition beanDefinition = AopConfigUtils.registerAspectJAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
		registerComponentIfNecessary(beanDefinition, parserContext);
	}

	public static void registerAspectJAnnotationAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {
		// Tips：此处的入参sourceElement即为"aop:aspectj-autoproxy"节点值

		// 【步骤1】：注册beanName（id）为org.springframework.aop.config.internalAutoProxyCreator的BeanDefinition
		BeanDefinition beanDefinition = AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));
		// 【步骤2】：对proxy-target-class以及expose-proxy属性进行处理
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
		// 【步骤3】：注册组件并通知(Fire an component-registered event)，便于监听器进一步处理
		registerComponentIfNecessary(beanDefinition, parserContext);
	}

	private static void useClassProxyingIfNecessary(BeanDefinitionRegistry registry, @Nullable Element sourceElement) {
		if (sourceElement != null) {
			// 对proxy-target-class属性进行处理
			// Tips：SpringAOP中支持使用的动态代理有两种：JDK动态代理和CGLib动态代理，
			// Spring会自动选择，但如果要强制使用CGLib动态代理，则需要将该proxy-target-class属性设置为true（默认是false）
			boolean proxyTargetClass = Boolean.parseBoolean(sourceElement.getAttribute(PROXY_TARGET_CLASS_ATTRIBUTE));
			if (proxyTargetClass) {
				AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
			}
			// 对expose-proxy属性进行处理
			// Tips：这个expose-proxy属性设置为true的作用是解决同一个类下在a方法调用b方法时没有触发执行b方法的增强方法的问题
			// 详见《Spring源码深度解析（第2版）的P181》
			boolean exposeProxy = Boolean.parseBoolean(sourceElement.getAttribute(EXPOSE_PROXY_ATTRIBUTE));
			if (exposeProxy) {
				AopConfigUtils.forceAutoProxyCreatorToExposeProxy(registry);
			}
		}
	}

	private static void registerComponentIfNecessary(@Nullable BeanDefinition beanDefinition, ParserContext parserContext) {
		if (beanDefinition != null) {
			parserContext.registerComponent(
					new BeanComponentDefinition(beanDefinition, AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME));
		}
	}

}
