/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.aop.framework.autoproxy;

import org.springframework.aop.Advisor;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.List;

/**
 * Generic auto proxy creator that builds AOP proxies for specific beans
 * based on detected Advisors for each bean.
 *
 * <p>Subclasses may override the {@link #findCandidateAdvisors()} method to
 * return a custom list of Advisors applying to any object. Subclasses can
 * also override the inherited {@link #shouldSkip} method to exclude certain
 * objects from auto-proxying.
 *
 * <p>Advisors or advices requiring ordering should implement the
 * {@link org.springframework.core.Ordered} interface. This class sorts
 * Advisors by Ordered order value. Advisors that don't implement the
 * Ordered interface will be considered as unordered; they will appear
 * at the end of the advisor chain in undefined order.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see #findCandidateAdvisors
 */
@SuppressWarnings("serial")
public abstract class AbstractAdvisorAutoProxyCreator extends AbstractAutoProxyCreator {

	@Nullable
	private BeanFactoryAdvisorRetrievalHelper advisorRetrievalHelper;

	// 覆写父类AbstractAutoProxyCreator所实现的BeanFactoryAware接口的setBeanFactory方法，
	// 并且在该覆写的方法中调用initBeanFactory方法，当然initBeanFactory方法为protected方法，同样可以被子类所覆写，并加上新的逻辑。【模板方法模式】
	// 那么此处的setBeanFactory方法是被谁所调用呢？
	// 答：由容器来回调，容器会回调所有实现了BeanFactoryAware接口的实现类的setBeanFactory方法，
	// 所以最此处所覆写的setBeanFactory方法就被容器所回调了。
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		super.setBeanFactory(beanFactory);
		if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
			throw new IllegalArgumentException(
					"AdvisorAutoProxyCreator requires a ConfigurableListableBeanFactory: " + beanFactory);
		}
		initBeanFactory((ConfigurableListableBeanFactory) beanFactory);
	}

	protected void initBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		this.advisorRetrievalHelper = new BeanFactoryAdvisorRetrievalHelperAdapter(beanFactory);
	}


	@Override
	@Nullable
	protected Object[] getAdvicesAndAdvisorsForBean(
			Class<?> beanClass, String beanName, @Nullable TargetSource targetSource) {

		// 为当前被自动代理的类（Bean）寻找合适的增强
		List<Advisor> advisors = findEligibleAdvisors(beanClass, beanName);
		if (advisors.isEmpty()) {
			// 没有找到适合的增强，则说明该Bean无法（无需）代理
			return DO_NOT_PROXY;
		}
		return advisors.toArray();
	}

	/**
	 * Find all eligible Advisors for auto-proxying this class.
	 * @param beanClass the clazz to find advisors for
	 * @param beanName the name of the currently proxied bean
	 * @return the empty List, not {@code null},
	 * if there are no pointcuts or interceptors
	 * @see #findCandidateAdvisors
	 * @see #sortAdvisors
	 * @see #extendAdvisors
	 */
	protected List<Advisor> findEligibleAdvisors(Class<?> beanClass, String beanName) {
		// 【步骤1】：获取所有增强作为候选
		// Tips：每次为当前Bean寻找适合的增强的时候，都先找到所有的增强，
		// 然后再从这所有的增强中去寻找适合当前Bean的增强，
		// 表明看上去性能可能会不太好，但实际上Spring会在获取所有的增强的逻辑中使用了缓存，
		// 之前已经找到的增强直接从缓存中获取就好了，无需再检测一遍，还是挺高效的。
		// 而且，先寻找所有的增强，再去适配，这样的逻辑也很清晰，
		// 更为关键的是：Spring是逐个Bean处理的，那么完全可能在某个Bean的后置处理器中增加了些增强（可能性存疑），
		// 因此在执行到下一个Bean时，完全有必要再次获取所有的增强（毕竟可能有更新了）。
		// ！这里给我们的一个启发是：善用缓存，这样子虽然看上去也是在一遍遍的执行全量查找逻辑，但性能是非常好的。
		// ！！！Tips：下面的findCandidateAdvisors方法并非仅在此处被调用，shouldSkip方法也调用了该方法。
		List<Advisor> candidateAdvisors = findCandidateAdvisors();
		// 【步骤2】：从所有候选的增强中找到适合应用当前Bean的增强
		// Tips:所谓的适合当前Bean的意思就是：根据上一步所获取的候选增强中所含有的切点信息去检测当前的Bean是否匹配，
		// 切记，当前Bean不是特指切面Bean，而可能是任一Bean，如果是当前为切面Bean的话，那经过下面的方法进行检测肯定是找不到匹配的增强的。
		List<Advisor> eligibleAdvisors = findAdvisorsThatCanApply(candidateAdvisors, beanClass, beanName);
		// 此处应用了【模板方法模式】，extendAdvisors方法为该抽象类中定义的一个protected空方法，子类可覆写
		extendAdvisors(eligibleAdvisors);
		if (!eligibleAdvisors.isEmpty()) {
			// 考虑到适用于该Bean的增强可能有多个的情况，此处还做了排序
			eligibleAdvisors = sortAdvisors(eligibleAdvisors);
		}
		return eligibleAdvisors;
	}

	/**
	 * Find all candidate Advisors to use in auto-proxying.
	 * @return the List of candidate Advisors
	 */
	protected List<Advisor> findCandidateAdvisors() {
		Assert.state(this.advisorRetrievalHelper != null, "No BeanFactoryAdvisorRetrievalHelper available");
		return this.advisorRetrievalHelper.findAdvisorBeans();
	}

	/**
	 * Search the given candidate Advisors to find all Advisors that
	 * can apply to the specified bean.
	 * @param candidateAdvisors the candidate Advisors
	 * @param beanClass the target's bean class
	 * @param beanName the target's bean name
	 * @return the List of applicable Advisors
	 * @see ProxyCreationContext#getCurrentProxiedBeanName()
	 */
	protected List<Advisor> findAdvisorsThatCanApply(
			List<Advisor> candidateAdvisors, Class<?> beanClass, String beanName) {
		// 设置当前正在代理的Bean实例的名称
		ProxyCreationContext.setCurrentProxiedBeanName(beanName);
		try {
			return AopUtils.findAdvisorsThatCanApply(candidateAdvisors, beanClass);
		}
		finally {
			ProxyCreationContext.setCurrentProxiedBeanName(null);
		}
	}

	/**
	 * Return whether the Advisor bean with the given name is eligible
	 * for proxying in the first place.
	 * @param beanName the name of the Advisor bean
	 * @return whether the bean is eligible
	 */
	protected boolean isEligibleAdvisorBean(String beanName) {
		return true;
	}

	/**
	 * Sort advisors based on ordering. Subclasses may choose to override this
	 * method to customize the sorting strategy.
	 * @param advisors the source List of Advisors
	 * @return the sorted List of Advisors
	 * @see org.springframework.core.Ordered
	 * @see org.springframework.core.annotation.Order
	 * @see org.springframework.core.annotation.AnnotationAwareOrderComparator
	 */
	protected List<Advisor> sortAdvisors(List<Advisor> advisors) {
		AnnotationAwareOrderComparator.sort(advisors);
		return advisors;
	}

	/**
	 * Extension hook that subclasses can override to register additional Advisors,
	 * given the sorted Advisors obtained to date.
	 * <p>The default implementation is empty.
	 * <p>Typically used to add Advisors that expose contextual information
	 * required by some of the later advisors.
	 * @param candidateAdvisors the Advisors that have already been identified as
	 * applying to a given bean
	 */
	protected void extendAdvisors(List<Advisor> candidateAdvisors) {
	}

	/**
	 * This auto-proxy creator always returns pre-filtered Advisors.
	 */
	@Override
	protected boolean advisorsPreFiltered() {
		return true;
	}


	/**
	 * Subclass of BeanFactoryAdvisorRetrievalHelper that delegates to
	 * surrounding AbstractAdvisorAutoProxyCreator facilities.
	 */
	private class BeanFactoryAdvisorRetrievalHelperAdapter extends BeanFactoryAdvisorRetrievalHelper {

		public BeanFactoryAdvisorRetrievalHelperAdapter(ConfigurableListableBeanFactory beanFactory) {
			super(beanFactory);
		}

		@Override
		protected boolean isEligibleBean(String beanName) {
			return AbstractAdvisorAutoProxyCreator.this.isEligibleAdvisorBean(beanName);
		}
	}

}
