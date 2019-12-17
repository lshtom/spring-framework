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

package org.springframework.aop.aspectj.annotation;

import org.aspectj.lang.reflect.PerClauseKind;
import org.springframework.aop.Advisor;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper for retrieving @AspectJ beans from a BeanFactory and building
 * Spring Advisors based on them, for use with auto-proxying.
 *
 * @author Juergen Hoeller
 * @since 2.0.2
 * @see AnnotationAwareAspectJAutoProxyCreator
 */
public class BeanFactoryAspectJAdvisorsBuilder {

	private final ListableBeanFactory beanFactory;

	private final AspectJAdvisorFactory advisorFactory;

	@Nullable
	private volatile List<String> aspectBeanNames;

	private final Map<String, List<Advisor>> advisorsCache = new ConcurrentHashMap<>();

	private final Map<String, MetadataAwareAspectInstanceFactory> aspectFactoryCache = new ConcurrentHashMap<>();


	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory) {
		this(beanFactory, new ReflectiveAspectJAdvisorFactory(beanFactory));
	}

	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 * @param advisorFactory the AspectJAdvisorFactory to build each Advisor with
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory, AspectJAdvisorFactory advisorFactory) {
		Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
		Assert.notNull(advisorFactory, "AspectJAdvisorFactory must not be null");
		this.beanFactory = beanFactory;
		this.advisorFactory = advisorFactory;
	}


	/**
	 * Look for AspectJ-annotated aspect beans in the current bean factory,
	 * and return to a list of Spring AOP Advisors representing them.
	 * <p>Creates a Spring Advisor for each AspectJ advice method.
	 * @return the list of {@link org.springframework.aop.Advisor} beans
	 * @see #isEligibleBean
	 */
	public List<Advisor> buildAspectJAdvisors() {
		// aspectBeanNames实例变量中缓存了使用AspectJ注解声明的Bean类的Bean名称
		List<String> aspectNames = this.aspectBeanNames;

		// 【步骤1】：若无缓存，则直接遍历容器中的Bean，并逐一解析，然后缓存
		if (aspectNames == null) {
			synchronized (this) {
				aspectNames = this.aspectBeanNames;
				if (aspectNames == null) {
					List<Advisor> advisors = new ArrayList<>();
					aspectNames = new ArrayList<>();
					String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
							this.beanFactory, Object.class, true, false);
					for (String beanName : beanNames) {
						// isEligibleBean方法用于判断该Bean是否是合适的Bean，
						// 如果返回false，则表示该Bean不是合适的Bean，则直接跳过不会再检测该Bean上是否有@AspectJ注解。
						// Tips：isEligibleBean是在当前类中定义的一个protected方法，默认返回false,
						// 子类可通过覆写该方法来在里面实现一些规则判断逻辑，【模板方法模式】
						// 这样子的话，使用者就可以通过配置规则来决定不对某些类进行AspectJ注解检测，从而不被AOP代理增强。
						// 当前类的子类AnnotationAwareAspectJAutoProxyCreator覆写类该方法，
						// 并加入可通过正则表达式来决定当前Bean是否为AspectJ Bean的逻辑，
						// 然后用户可利用该类的setIncludePatterns方法来添加上相应的正则表达式。
						if (!isEligibleBean(beanName)) {
							continue;
						}
						// We must be careful not to instantiate beans eagerly as in this case they
						// would be cached by the Spring container but would not have been weaved.
						Class<?> beanType = this.beanFactory.getType(beanName);
						if (beanType == null) {
							continue;
						}
						// 判断当前Bean的类型是否为AspectJ切面（标注上@AspectJ注解的Bean类），
						// 如果是切面类，则接下来要获取该类中所定义的增强。
						// !Tips：此处所谓的单前Bean，指的是当前的for循环正在遍历的Bean！
						if (this.advisorFactory.isAspect(beanType)) {
							aspectNames.add(beanName);
							// 对该AspectJ切面类进行下封装，封装成一个Aspect元数据类
							AspectMetadata amd = new AspectMetadata(beanType, beanName);
							// 判断该切面的实例化模型是否为单例
							if (amd.getAjType().getPerClause().getKind() == PerClauseKind.SINGLETON) {
								// 注意，该factory实例中封装了beanFactory实例，
								// 并且在BeanFactoryAspectInstanceFactory的构造器中会通过所传入的beanFactory实例根据beanName来获取该切面Bean类的相关信息，
								// 并封装出AspectMetadata类型（在内部），之后在advisorFactory.getAdvisors中会用到。
								MetadataAwareAspectInstanceFactory factory =
										new BeanFactoryAspectInstanceFactory(this.beanFactory, beanName);
								// 获取增强，如果该切面Bean为单例Bean的话那么还会对增强进行缓存！
								List<Advisor> classAdvisors = this.advisorFactory.getAdvisors(factory);
								if (this.beanFactory.isSingleton(beanName)) {
									this.advisorsCache.put(beanName, classAdvisors);
								}
								else {
									this.aspectFactoryCache.put(beanName, factory);
								}
								advisors.addAll(classAdvisors);
							}
							else {
								// Per target or per this.
								// Bean是单例Bean，但其切面的实例化模型非单例则报错
								if (this.beanFactory.isSingleton(beanName)) {
									throw new IllegalArgumentException("Bean with name '" + beanName +
											"' is a singleton, but aspect instantiation model is not singleton");
								}
								// 非单例，那么处理方式还是获取所有的增强，但是不会对其进行缓存，这也是与上面最大的区别！！！
								MetadataAwareAspectInstanceFactory factory =
										new PrototypeAspectInstanceFactory(this.beanFactory, beanName);
								this.aspectFactoryCache.put(beanName, factory);
								advisors.addAll(this.advisorFactory.getAdvisors(factory));
							}
						}
					}
					this.aspectBeanNames = aspectNames;
					return advisors;
				}
			}
		}

		// 【步骤2】：有缓存，但缓存的内容为空，则直接返回
		if (aspectNames.isEmpty()) {
			return Collections.emptyList();
		}

		// 【步骤3】：有缓存，且缓存的内容非空，则直接从缓存中获取返回
		List<Advisor> advisors = new ArrayList<>();
		for (String aspectName : aspectNames) {
			List<Advisor> cachedAdvisors = this.advisorsCache.get(aspectName);
			// 在获取所缓存的增强的时候，又有两种情况：
			// 情况1：该切面的实例模型为单例且该Bean为单例，则直接获取所缓存的增强并返回
			if (cachedAdvisors != null) {
				advisors.addAll(cachedAdvisors);
			}
			// 情况2：非单例，故只缓存了工厂，故先获取工厂，再从工厂中获取增强
			else {
				MetadataAwareAspectInstanceFactory factory = this.aspectFactoryCache.get(aspectName);
				advisors.addAll(this.advisorFactory.getAdvisors(factory));
			}
		}
		return advisors;
	}

	/**
	 * Return whether the aspect bean with the given name is eligible.
	 * @param beanName the name of the aspect bean
	 * @return whether the bean is eligible
	 */
	protected boolean isEligibleBean(String beanName) {
		return true;
	}

}
