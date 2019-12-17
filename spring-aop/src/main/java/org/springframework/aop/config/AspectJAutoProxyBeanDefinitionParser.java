/*
 * Copyright 2002-2016 the original author or authors.
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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.lang.Nullable;

/**
 * {@link BeanDefinitionParser} for the {@code aspectj-autoproxy} tag,
 * enabling the automatic application of @AspectJ-style aspects found in
 * the {@link org.springframework.beans.factory.BeanFactory}.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @since 2.0
 */
class AspectJAutoProxyBeanDefinitionParser implements BeanDefinitionParser {

	@Override
	@Nullable
	public BeanDefinition parse(Element element, ParserContext parserContext) {
		// Tips：一般我们在实现自定义标签的解析器的时候，通常是直接继承AbstractSingleBeanDefinitionParser,
		// 然后覆写其doParse方法，这么做的好处是我们无需自己去解决诸如父子标签等问题，只需专注进行属性值的解析即可，
		// 但是，此处的<aop:aspectj-autoproxy/>标签的解析器却没有继承自AbstractSingleBeanDefinitionParser,
		// 而是直接实现了BeanDefinitionParser接口,这是为何呢？
		// 原因在于：<aop:aspectj-autoproxy/>标签的功能是支持AOP注解、开启AOP功能，而不是对XML中的某某属性值进行解析，
		// 因此就用不上AbstractSingleBeanDefinitionParser所包装的那些逻辑了。

		// Tips：前面在分析自定义标签的解析的时候提到过，
		// Spring在parseCustomElement方法中先获取相应的NamespaceHandler处理器实例，
		// 并最终调用到相应解析的parse方法，其入参ParserContext实例中是包含了ReaderContext对象，
		// 而ReaderContext对象内部是持有rigstry对象的，
		// 因此意味着可利用ParserContext对象实例来获取/注册Bean定义信息。

		// 【步骤1】：注册AnnotationAwareAspectJAutoProxyCreator
		AopNamespaceUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(parserContext, element);
		// 【步骤2】：对于上面的步骤1中所注册的BeanDefinition中所含/可能含的子节点进行处理
		extendBeanDefinition(element, parserContext);
		return null;
	}

	private void extendBeanDefinition(Element element, ParserContext parserContext) {
		// 此处获取的名为org.springframework.aop.config.internalAutoProxyCreator的BeanDefinition
		// 其实就是在上面的registerAspectJAnnotationAutoProxyCreatorIfNecessary方法的内部逻辑中注册BeanDefinition,
		// 而此处就是对这个BeanDefinition进行扩展处理，主要是针对其内部还含有子标签节点的情况。
		BeanDefinition beanDef =
				parserContext.getRegistry().getBeanDefinition(AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME);
		// 默认情况下并没有子节点
		if (element.hasChildNodes()) {
			addIncludePatterns(element, parserContext, beanDef);
		}
	}

	private void addIncludePatterns(Element element, ParserContext parserContext, BeanDefinition beanDef) {
		ManagedList<TypedStringValue> includePatterns = new ManagedList<>();
		NodeList childNodes = element.getChildNodes();
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node node = childNodes.item(i);
			if (node instanceof Element) {
				Element includeElement = (Element) node;
				TypedStringValue valueHolder = new TypedStringValue(includeElement.getAttribute("name"));
				valueHolder.setSource(parserContext.extractSource(includeElement));
				includePatterns.add(valueHolder);
			}
		}
		if (!includePatterns.isEmpty()) {
			includePatterns.setSource(parserContext.extractSource(element));
			beanDef.getPropertyValues().add("includePatterns", includePatterns);
		}
	}

}
