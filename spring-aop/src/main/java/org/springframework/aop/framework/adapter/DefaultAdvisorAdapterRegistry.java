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

package org.springframework.aop.framework.adapter;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of the {@link AdvisorAdapterRegistry} interface.
 * Supports {@link org.aopalliance.intercept.MethodInterceptor},
 * {@link org.springframework.aop.MethodBeforeAdvice},
 * {@link org.springframework.aop.AfterReturningAdvice},
 * {@link org.springframework.aop.ThrowsAdvice}.
 *
 * @author Rod Johnson
 * @author Rob Harrop
 * @author Juergen Hoeller
 */
@SuppressWarnings("serial")
public class DefaultAdvisorAdapterRegistry implements AdvisorAdapterRegistry, Serializable {

	private final List<AdvisorAdapter> adapters = new ArrayList<>(3);


	/**
	 * Create a new DefaultAdvisorAdapterRegistry, registering well-known adapters.
	 */
	public DefaultAdvisorAdapterRegistry() {
		registerAdvisorAdapter(new MethodBeforeAdviceAdapter());
		registerAdvisorAdapter(new AfterReturningAdviceAdapter());
		registerAdvisorAdapter(new ThrowsAdviceAdapter());
	}


	@Override
	public Advisor wrap(Object adviceObject) throws UnknownAdviceTypeException {
		// Tips:该方法的逻辑就是：将增强对象包装成Advisor对象，如果无法包装成则抛出异常！

		// 情形一：当前增强对象已经是Advisor类型了，无需再包装，直接返回
		if (adviceObject instanceof Advisor) {
			return (Advisor) adviceObject;
		}
		// 情形二：不是Advisor，也不是Advice，直接抛出异常
		if (!(adviceObject instanceof Advice)) {
			throw new UnknownAdviceTypeException(adviceObject);
		}
		// 情形三：是Advice，则尝试能否包装成Advisor，若可以则包装好后返回，否则抛出异常
		Advice advice = (Advice) adviceObject;
		if (advice instanceof MethodInterceptor) {
			// So well-known it doesn't even need an adapter.
			return new DefaultPointcutAdvisor(advice);
		}
		for (AdvisorAdapter adapter : this.adapters) {
			// Check that it is supported.
			if (adapter.supportsAdvice(advice)) {
				return new DefaultPointcutAdvisor(advice);
			}
		}
		throw new UnknownAdviceTypeException(advice);
	}

	@Override
	public MethodInterceptor[] getInterceptors(Advisor advisor) throws UnknownAdviceTypeException {
		List<MethodInterceptor> interceptors = new ArrayList<>(3);
		Advice advice = advisor.getAdvice();
		if (advice instanceof MethodInterceptor) {
			interceptors.add((MethodInterceptor) advice);
		}
		for (AdvisorAdapter adapter : this.adapters) {
			if (adapter.supportsAdvice(advice)) {
				// 在具体的getInterceptor方法的实现中用到了【适配器模式】，
				// 比如在MethodBeforeAdviceAdapter类所实现的getInterceptor方法中
				// 用MethodBeforeAdviceInterceptor类对MethodBeforeAdvice类型的advice进行了包装，以适配成拦截器。
				interceptors.add(adapter.getInterceptor(advisor));
			}
		}
		if (interceptors.isEmpty()) {
			throw new UnknownAdviceTypeException(advisor.getAdvice());
		}
		return interceptors.toArray(new MethodInterceptor[0]);
	}

	@Override
	public void registerAdvisorAdapter(AdvisorAdapter adapter) {
		this.adapters.add(adapter);
	}

}
