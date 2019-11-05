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

package org.springframework.beans.factory.support;

import org.apache.commons.logging.Log;
import org.springframework.beans.*;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.*;
import org.springframework.core.*;
import org.springframework.lang.Nullable;
import org.springframework.util.*;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Abstract bean factory superclass that implements default bean creation,
 * with the full capabilities specified by the {@link RootBeanDefinition} class.
 * Implements the {@link org.springframework.beans.factory.config.AutowireCapableBeanFactory}
 * interface in addition to AbstractBeanFactory's {@link #createBean} method.
 *
 * <p>Provides bean creation (with constructor resolution), property population,
 * wiring (including autowiring), and initialization. Handles runtime bean
 * references, resolves managed collections, calls initialization methods, etc.
 * Supports autowiring constructors, properties by name, and properties by type.
 *
 * <p>The main template method to be implemented by subclasses is
 * {@link #resolveDependency(DependencyDescriptor, String, Set, TypeConverter)},
 * used for autowiring by type. In case of a factory which is capable of searching
 * its bean definitions, matching beans will typically be implemented through such
 * a search. For other factory styles, simplified matching algorithms can be implemented.
 *
 * <p>Note that this class does <i>not</i> assume or implement bean definition
 * registry capabilities. See {@link DefaultListableBeanFactory} for an implementation
 * of the {@link org.springframework.beans.factory.ListableBeanFactory} and
 * {@link BeanDefinitionRegistry} interfaces, which represent the API and SPI
 * view of such a factory, respectively.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Mark Fisher
 * @author Costin Leau
 * @author Chris Beams
 * @author Sam Brannen
 * @since 13.02.2004
 * @see RootBeanDefinition
 * @see DefaultListableBeanFactory
 * @see BeanDefinitionRegistry
 */
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory
		implements AutowireCapableBeanFactory {

	/** Strategy for creating bean instances. */
	private InstantiationStrategy instantiationStrategy = new CglibSubclassingInstantiationStrategy();

	/** Resolver strategy for method parameter names. */
	@Nullable
	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	/** Whether to automatically try to resolve circular references between beans. */
	private boolean allowCircularReferences = true;

	/**
	 * Whether to resort to injecting a raw bean instance in case of circular reference,
	 * even if the injected bean eventually got wrapped.
	 */
	private boolean allowRawInjectionDespiteWrapping = false;

	/**
	 * Dependency types to ignore on dependency check and autowire, as Set of
	 * Class objects: for example, String. Default is none.
	 */
	private final Set<Class<?>> ignoredDependencyTypes = new HashSet<>();

	/**
	 * Dependency interfaces to ignore on dependency check and autowire, as Set of
	 * Class objects. By default, only the BeanFactory interface is ignored.
	 */
	private final Set<Class<?>> ignoredDependencyInterfaces = new HashSet<>();

	/**
	 * The name of the currently created bean, for implicit dependency registration
	 * on getBean etc invocations triggered from a user-specified Supplier callback.
	 */
	private final NamedThreadLocal<String> currentlyCreatedBean = new NamedThreadLocal<>("Currently created bean");

	/** Cache of unfinished FactoryBean instances: FactoryBean name to BeanWrapper. */
	private final ConcurrentMap<String, BeanWrapper> factoryBeanInstanceCache = new ConcurrentHashMap<>();

	/** Cache of candidate factory methods per factory class. */
	private final ConcurrentMap<Class<?>, Method[]> factoryMethodCandidateCache = new ConcurrentHashMap<>();

	/** Cache of filtered PropertyDescriptors: bean Class to PropertyDescriptor array. */
	private final ConcurrentMap<Class<?>, PropertyDescriptor[]> filteredPropertyDescriptorsCache =
			new ConcurrentHashMap<>();


	/**
	 * Create a new AbstractAutowireCapableBeanFactory.
	 */
	public AbstractAutowireCapableBeanFactory() {
		super();
		ignoreDependencyInterface(BeanNameAware.class);
		ignoreDependencyInterface(BeanFactoryAware.class);
		ignoreDependencyInterface(BeanClassLoaderAware.class);
	}

	/**
	 * Create a new AbstractAutowireCapableBeanFactory with the given parent.
	 * @param parentBeanFactory parent bean factory, or {@code null} if none
	 */
	public AbstractAutowireCapableBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		this();
		setParentBeanFactory(parentBeanFactory);
	}


	/**
	 * Set the instantiation strategy to use for creating bean instances.
	 * Default is CglibSubclassingInstantiationStrategy.
	 * @see CglibSubclassingInstantiationStrategy
	 */
	public void setInstantiationStrategy(InstantiationStrategy instantiationStrategy) {
		this.instantiationStrategy = instantiationStrategy;
	}

	/**
	 * Return the instantiation strategy to use for creating bean instances.
	 */
	protected InstantiationStrategy getInstantiationStrategy() {
		return this.instantiationStrategy;
	}

	/**
	 * Set the ParameterNameDiscoverer to use for resolving method parameter
	 * names if needed (e.g. for constructor names).
	 * <p>Default is a {@link DefaultParameterNameDiscoverer}.
	 */
	public void setParameterNameDiscoverer(@Nullable ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	/**
	 * Return the ParameterNameDiscoverer to use for resolving method parameter
	 * names if needed.
	 */
	@Nullable
	protected ParameterNameDiscoverer getParameterNameDiscoverer() {
		return this.parameterNameDiscoverer;
	}

	/**
	 * Set whether to allow circular references between beans - and automatically
	 * try to resolve them.
	 * <p>Note that circular reference resolution means that one of the involved beans
	 * will receive a reference to another bean that is not fully initialized yet.
	 * This can lead to subtle and not-so-subtle side effects on initialization;
	 * it does work fine for many scenarios, though.
	 * <p>Default is "true". Turn this off to throw an exception when encountering
	 * a circular reference, disallowing them completely.
	 * <p><b>NOTE:</b> It is generally recommended to not rely on circular references
	 * between your beans. Refactor your application logic to have the two beans
	 * involved delegate to a third bean that encapsulates their common logic.
	 */
	public void setAllowCircularReferences(boolean allowCircularReferences) {
		this.allowCircularReferences = allowCircularReferences;
	}

	/**
	 * Set whether to allow the raw injection of a bean instance into some other
	 * bean's property, despite the injected bean eventually getting wrapped
	 * (for example, through AOP auto-proxying).
	 * <p>This will only be used as a last resort in case of a circular reference
	 * that cannot be resolved otherwise: essentially, preferring a raw instance
	 * getting injected over a failure of the entire bean wiring process.
	 * <p>Default is "false", as of Spring 2.0. Turn this on to allow for non-wrapped
	 * raw beans injected into some of your references, which was Spring 1.2's
	 * (arguably unclean) default behavior.
	 * <p><b>NOTE:</b> It is generally recommended to not rely on circular references
	 * between your beans, in particular with auto-proxying involved.
	 * @see #setAllowCircularReferences
	 */
	public void setAllowRawInjectionDespiteWrapping(boolean allowRawInjectionDespiteWrapping) {
		this.allowRawInjectionDespiteWrapping = allowRawInjectionDespiteWrapping;
	}

	/**
	 * Ignore the given dependency type for autowiring:
	 * for example, String. Default is none.
	 */
	public void ignoreDependencyType(Class<?> type) {
		this.ignoredDependencyTypes.add(type);
	}

	/**
	 * Ignore the given dependency interface for autowiring.
	 * <p>This will typically be used by application contexts to register
	 * dependencies that are resolved in other ways, like BeanFactory through
	 * BeanFactoryAware or ApplicationContext through ApplicationContextAware.
	 * <p>By default, only the BeanFactoryAware interface is ignored.
	 * For further types to ignore, invoke this method for each type.
	 * @see org.springframework.beans.factory.BeanFactoryAware
	 * @see org.springframework.context.ApplicationContextAware
	 */
	public void ignoreDependencyInterface(Class<?> ifc) {
		this.ignoredDependencyInterfaces.add(ifc);
	}

	@Override
	public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
		super.copyConfigurationFrom(otherFactory);
		if (otherFactory instanceof AbstractAutowireCapableBeanFactory) {
			AbstractAutowireCapableBeanFactory otherAutowireFactory =
					(AbstractAutowireCapableBeanFactory) otherFactory;
			this.instantiationStrategy = otherAutowireFactory.instantiationStrategy;
			this.allowCircularReferences = otherAutowireFactory.allowCircularReferences;
			this.ignoredDependencyTypes.addAll(otherAutowireFactory.ignoredDependencyTypes);
			this.ignoredDependencyInterfaces.addAll(otherAutowireFactory.ignoredDependencyInterfaces);
		}
	}


	//-------------------------------------------------------------------------
	// Typical methods for creating and populating external bean instances
	//-------------------------------------------------------------------------

	@Override
	@SuppressWarnings("unchecked")
	public <T> T createBean(Class<T> beanClass) throws BeansException {
		// Use prototype bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(beanClass);
		bd.setScope(SCOPE_PROTOTYPE);
		bd.allowCaching = ClassUtils.isCacheSafe(beanClass, getBeanClassLoader());
		return (T) createBean(beanClass.getName(), bd, null);
	}

	@Override
	public void autowireBean(Object existingBean) {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(ClassUtils.getUserClass(existingBean));
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bd.allowCaching = ClassUtils.isCacheSafe(bd.getBeanClass(), getBeanClassLoader());
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(bd.getBeanClass().getName(), bd, bw);
	}

	@Override
	public Object configureBean(Object existingBean, String beanName) throws BeansException {
		markBeanAsCreated(beanName);
		BeanDefinition mbd = getMergedBeanDefinition(beanName);
		RootBeanDefinition bd = null;
		if (mbd instanceof RootBeanDefinition) {
			RootBeanDefinition rbd = (RootBeanDefinition) mbd;
			bd = (rbd.isPrototype() ? rbd : rbd.cloneBeanDefinition());
		}
		if (bd == null) {
			bd = new RootBeanDefinition(mbd);
		}
		if (!bd.isPrototype()) {
			bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
			bd.allowCaching = ClassUtils.isCacheSafe(ClassUtils.getUserClass(existingBean), getBeanClassLoader());
		}
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(beanName, bd, bw);
		return initializeBean(beanName, existingBean, bd);
	}


	//-------------------------------------------------------------------------
	// Specialized methods for fine-grained control over the bean lifecycle
	//-------------------------------------------------------------------------

	@Override
	public Object createBean(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(beanClass, autowireMode, dependencyCheck);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		return createBean(beanClass.getName(), bd, null);
	}

	@Override
	public Object autowire(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		final RootBeanDefinition bd = new RootBeanDefinition(beanClass, autowireMode, dependencyCheck);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		if (bd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR) {
			return autowireConstructor(beanClass.getName(), bd, null, null).getWrappedInstance();
		}
		else {
			Object bean;
			final BeanFactory parent = this;
			if (System.getSecurityManager() != null) {
				bean = AccessController.doPrivileged((PrivilegedAction<Object>) () ->
						getInstantiationStrategy().instantiate(bd, null, parent),
						getAccessControlContext());
			}
			else {
				bean = getInstantiationStrategy().instantiate(bd, null, parent);
			}
			populateBean(beanClass.getName(), bd, new BeanWrapperImpl(bean));
			return bean;
		}
	}

	@Override
	public void autowireBeanProperties(Object existingBean, int autowireMode, boolean dependencyCheck)
			throws BeansException {

		if (autowireMode == AUTOWIRE_CONSTRUCTOR) {
			throw new IllegalArgumentException("AUTOWIRE_CONSTRUCTOR not supported for existing bean instance");
		}
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd =
				new RootBeanDefinition(ClassUtils.getUserClass(existingBean), autowireMode, dependencyCheck);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(bd.getBeanClass().getName(), bd, bw);
	}

	@Override
	public void applyBeanPropertyValues(Object existingBean, String beanName) throws BeansException {
		markBeanAsCreated(beanName);
		BeanDefinition bd = getMergedBeanDefinition(beanName);
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		applyPropertyValues(beanName, bd, bw, bd.getPropertyValues());
	}

	@Override
	public Object initializeBean(Object existingBean, String beanName) {
		return initializeBean(beanName, existingBean, null);
	}

	@Override
	public Object applyBeanPostProcessorsBeforeInitialization(Object existingBean, String beanName)
			throws BeansException {

		Object result = existingBean;
		for (BeanPostProcessor processor : getBeanPostProcessors()) {
			Object current = processor.postProcessBeforeInitialization(result, beanName);
			if (current == null) {
				return result;
			}
			result = current;
		}
		return result;
	}

	@Override
	public Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName)
			throws BeansException {

		Object result = existingBean;
		for (BeanPostProcessor processor : getBeanPostProcessors()) {
			Object current = processor.postProcessAfterInitialization(result, beanName);
			if (current == null) {
				return result;
			}
			result = current;
		}
		return result;
	}

	@Override
	public void destroyBean(Object existingBean) {
		new DisposableBeanAdapter(existingBean, getBeanPostProcessors(), getAccessControlContext()).destroy();
	}


	//-------------------------------------------------------------------------
	// Delegate methods for resolving injection points
	//-------------------------------------------------------------------------

	@Override
	public Object resolveBeanByName(String name, DependencyDescriptor descriptor) {
		InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);
		try {
			return getBean(name, descriptor.getDependencyType());
		}
		finally {
			ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
		}
	}

	@Override
	@Nullable
	public Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName) throws BeansException {
		return resolveDependency(descriptor, requestingBeanName, null, null);
	}


	//---------------------------------------------------------------------
	// Implementation of relevant AbstractBeanFactory template methods
	//---------------------------------------------------------------------

	/**
	 * Central method of this class: creates a bean instance,
	 * populates the bean instance, applies post-processors, etc.
	 * @see #doCreateBean
	 */
	@Override
	protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {

		if (logger.isTraceEnabled()) {
			logger.trace("Creating instance of bean '" + beanName + "'");
		}
		RootBeanDefinition mbdToUse = mbd;

		// Make sure bean class is actually resolved at this point, and
		// clone the bean definition in case of a dynamically resolved Class
		// which cannot be stored in the shared merged bean definition.
		// [译]确保此时bean类已实际解析，并克隆bean定义，以防无法在共享合并bean定义中存储动态解析的类。
		// 【步骤1】：解析并获取当前要创建的Bean的Class对象实例，并判断是否为动态解析的类，若是则克隆Bean的定义
		Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
		if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
			mbdToUse = new RootBeanDefinition(mbd);
			mbdToUse.setBeanClass(resolvedClass);
		}

		// Prepare method overrides.
		// 【步骤2】：验证及准备覆盖的方法
		// 说明：在Bean的定义中使用到了lookup-method和replace-method会涉及到此
		try {
			mbdToUse.prepareMethodOverrides();
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
					beanName, "Validation of method overrides failed", ex);
		}

		try {
			// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
			// 【步骤3】：在该Bean实例化前调用相应的后置处理器
			// 说明：这也是Spring提供的一个扩展点，通过回调后置处理器的方式来让开发人员来进行Bean的创建，
			// 而并非是直接使用Spring所创建的Bean.
			// 虽然上面的英文注释中说了提供一个机会来返回代理实例，但实际的做法中生成代理类并非在此处，
			// 毕竟通常的做法是在已经产生的Bean对象实例的基础上来生成代理类，而此处连Bean的实例都没有被创建过
			Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
			if (bean != null) {
				return bean;
			}
		}
		catch (Throwable ex) {
			throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
					"BeanPostProcessor before instantiation of bean failed", ex);
		}

		try {
			// 【步骤4】：进行真正的Bean实例的创建
			Object beanInstance = doCreateBean(beanName, mbdToUse, args);
			if (logger.isTraceEnabled()) {
				logger.trace("Finished creating instance of bean '" + beanName + "'");
			}
			return beanInstance;
		}
		catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
			// A previously detected exception with proper bean creation context already,
			// or illegal singleton state to be communicated up to DefaultSingletonBeanRegistry.
			throw ex;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
		}
	}

	/**
	 * Actually create the specified bean. Pre-creation processing has already happened
	 * at this point, e.g. checking {@code postProcessBeforeInstantiation} callbacks.
	 * <p>Differentiates between default bean instantiation, use of a
	 * factory method, and autowiring a constructor.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a new instance of the bean
	 * @throws BeanCreationException if the bean could not be created
	 * @see #instantiateBean
	 * @see #instantiateUsingFactoryMethod
	 * @see #autowireConstructor
	 */
	protected Object doCreateBean(final String beanName, final RootBeanDefinition mbd, final @Nullable Object[] args)
			throws BeanCreationException {

		// Instantiate the bean.
		// 【步骤1】：进行Bean的实例化（即此时的Bean实例仅仅是通过构造器创建出来的而已，里面的属性还是没有值的）
		BeanWrapper instanceWrapper = null;
		// 是单例的话就先清除缓存
		if (mbd.isSingleton()) {
			// remove方法的作用就是从缓存中获取相应的实例并移除该实例，如果缓存中本身没有的话，那就是空
			instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
		}
		if (instanceWrapper == null) {
			// 缓存中没有Bean实例，那么就根据该Bean所指定的策略创建出相应的Bean实例，
			// 比如工厂方法、构造器自动注入、简单初始化等
			instanceWrapper = createBeanInstance(beanName, mbd, args);
		}
		final Object bean = instanceWrapper.getWrappedInstance();
		Class<?> beanType = instanceWrapper.getWrappedClass();
		if (beanType != NullBean.class) {
			mbd.resolvedTargetType = beanType;
		}

		// Allow post-processors to modify the merged bean definition.
		// 【步骤2】：应用MergedBeanDefinitionPostProcessor后置处理器，
		// 该后置处理器的作用是允许用户去修改Bean的定义信息，
		// 因此程序运行到此处也仅仅是完成了Bean的实例化(尚未初始化)，
		// 但里面的属性注入以及Bean创建过程中所经历的一系列生命周期调用尚未开始.
		synchronized (mbd.postProcessingLock) {
			// 实例变量postProcessed的默认值为false
			if (!mbd.postProcessed) {
				try {
					applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
				}
				catch (Throwable ex) {
					throw new BeanCreationException(mbd.getResourceDescription(), beanName,
							"Post-processing of merged bean definition failed", ex);
				}
				mbd.postProcessed = true;
			}
		}

		// Eagerly cache singletons to be able to resolve circular references
		// even when triggered by lifecycle interfaces like BeanFactoryAware.
		// 【步骤3】：循环依赖预处理
		// 处理逻辑：检测是否允许提前曝光尚未完成初始化的Bean实例（此时的Bean仅仅是刚创建出来）,
		// 若允许则将创建实例的ObjectFactory加入到工厂中进行缓存，
		// 后面当在Bean初始化阶段需要解决setter方法的循环依赖注入问题时，
		// 可以通过获取相应的ObjectFactory来获取待注入的Bean实例从而解决循环依赖问题。
		boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
				isSingletonCurrentlyInCreation(beanName));
		if (earlySingletonExposure) {
			if (logger.isTraceEnabled()) {
				logger.trace("Eagerly caching bean '" + beanName +
						"' to allow for resolving potential circular references");
			}
			// getEarlyBeanReference方法的入参bean其实是前面已经进行了实例化的Bean对象实例，
			// 而在getEarlyBeanReference方法中会回调SmartInstantiationAwareBeanPostProcessor类型的后置处理器的getEarlyBeanReference方法，
			// 该后置处理器的方法提供了一个机会来让用户返回新实例（比如对传入的bean实例进行包装），
			// AOP就是在此处将advice动态织入Bean中的
			addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
			// 上面这段逻辑等同的匿名内部类的写法如下：
			/**
			addSingletonFactory(beanName, new ObjectFactory<Object>() {
				@Override
				public Object getObject() throws BeansException {
					return getEarlyBeanReference(beanName, mbd, bean);
				}
			});
			*/
			// 此处的作用就是：在将Bean初始化完成前将创建Bean实例的ObjectFactory实例加入到工厂缓存中，
			// 后面在进行Bean初始化时需要进行循环依赖的解决时，可以从该工厂缓存中拿到相应的ObjectFactory实例，
			// 然后调用getObject方法来获取所依赖的实例，从而解决settter循环依赖的注入问题。
			// Tips：这里有个问题必须要明白的是：addSingletonFactory方法中缓存的仅仅是ObjectFactory对象实例，
			// 而并非将bean对象也缓存了，但为何后面在解决循环依赖的时候能通过ObjectFactory的getObject方法获取到bean对象实例呢？
			// 个人认为是：这整个过程就是一个大的递归调用，之前的bean实例其实是存在了栈区中了
		}

		// Initialize the bean instance.
		// 【步骤4】：对Bean实例进行初始化
		// 在Bean创建的所经历的生命周期中所回调的各种方法基本是在此被调用的
		Object exposedObject = bean;
		try {
			// 4.1 对当前Bean中的各个属性进行填充
			populateBean(beanName, mbd, instanceWrapper);
			// 4.2 调用相应的Bean初始化方法进行初始化
			exposedObject = initializeBean(beanName, exposedObject, mbd);
		}
		catch (Throwable ex) {
			if (ex instanceof BeanCreationException && beanName.equals(((BeanCreationException) ex).getBeanName())) {
				throw (BeanCreationException) ex;
			}
			else {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Initialization of bean failed", ex);
			}
		}

		// 【步骤5】：循环依赖检查：主要是检查是否有未解决的循环依赖问题，如果有则报错
		if (earlySingletonExposure) {
			// 在getSingleton方法中，会首先尝试从单例对象缓存singletonObjects中获取当前Bean实例，
			// 由于此时仍处于创建中，所以从singletonObjects中拿到的为null，
			// 接着会尝试从earlySingletonObjects中去获取，
			// 在没有被循环依赖的情况下，从earlySingletonObjects中是获取不到的，
			// 因此，earlySingletonReference只有在检测到有循环依赖的情况下才会不为空
			Object earlySingletonReference = getSingleton(beanName, false);
			if (earlySingletonReference != null) {
				// 两者相等，表明没有在初始化方法initializeBean中被改变，即没有被增强
				// Tips：在上面的步骤4.2中所调用的initializeBean方法中调用的BeanPostProcessor后置处理方法postProcessBeforeInitialization
				// 和postProcessAfterInitialization方法可能会对Bean进行包装，进行增强，返回代理类对象，
				// 因此两者就会出现不等。
				if (exposedObject == bean) {
					// 为啥要用earlySingletonReference赋值给exposedObject呢？疑问
					exposedObject = earlySingletonReference;
				}
				else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
					// 如果获取到的dependentBeans非空，则表明可能存在未解决的循环依赖，
					// 下面要做进一步检测，得到真正的依赖
					String[] dependentBeans = getDependentBeans(beanName);
					Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
					for (String dependentBean : dependentBeans) {
						// 依赖检测：removeSingletonIfCreatedForTypeCheckOnly方法中检测dependentBean是否已经完成了创建，
						// 如果还没创建，则返回false，也就说明这个依赖是没有解决的，然后加入得到actualDependentBeans中
						// 这注释不对！感觉Spring是对于被增强的Bean，如果检测到有循环依赖的,都是被认为非正常情况。
						// 上面的注释并不正确，返回false并非意味着没创建！！！
						if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
							actualDependentBeans.add(dependentBean);
						}
					}
					// 对于已经解决(能解决)的循环依赖，当前Bean所依赖的其它Bean的实例肯定是已经被创建了的，
					// 如果存在未创建的，那也说明着依赖无法解决了，所以报错。
					// 这个注释也不对！！！下面的报错的报错信息才是真正的官方解析！
					if (!actualDependentBeans.isEmpty()) {
						throw new BeanCurrentlyInCreationException(beanName,
								"Bean with name '" + beanName + "' has been injected into other beans [" +
								StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
								"] in its raw version as part of a circular reference, but has eventually been " +
								"wrapped. This means that said other beans do not use the final version of the " +
								"bean. This is often the result of over-eager type matching - consider using " +
								"'getBeanNamesOfType' with the 'allowEagerInit' flag turned off, for example.");
					}
				}
			}
		}

		// Register bean as disposable.
		// 【步骤6】：注册DisposableBean
		try {
			registerDisposableBeanIfNecessary(beanName, bean, mbd);
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
		}

		// 【步骤7】：返回创建好的对象
		return exposedObject;
	}

	@Override
	@Nullable
	protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		Class<?> targetType = determineTargetType(beanName, mbd, typesToMatch);

		// Apply SmartInstantiationAwareBeanPostProcessors to predict the
		// eventual type after a before-instantiation shortcut.
		if (targetType != null && !mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
					SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
					Class<?> predicted = ibp.predictBeanType(targetType, beanName);
					if (predicted != null && (typesToMatch.length != 1 || FactoryBean.class != typesToMatch[0] ||
							FactoryBean.class.isAssignableFrom(predicted))) {
						return predicted;
					}
				}
			}
		}
		return targetType;
	}

	/**
	 * Determine the target type for the given bean definition.
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the type for the bean if determinable, or {@code null} otherwise
	 */
	@Nullable
	protected Class<?> determineTargetType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		Class<?> targetType = mbd.getTargetType();
		if (targetType == null) {
			targetType = (mbd.getFactoryMethodName() != null ?
					getTypeForFactoryMethod(beanName, mbd, typesToMatch) :
					resolveBeanClass(mbd, beanName, typesToMatch));
			if (ObjectUtils.isEmpty(typesToMatch) || getTempClassLoader() == null) {
				mbd.resolvedTargetType = targetType;
			}
		}
		return targetType;
	}

	/**
	 * Determine the target type for the given bean definition which is based on
	 * a factory method. Only called if there is no singleton instance registered
	 * for the target bean already.
	 * <p>This implementation determines the type matching {@link #createBean}'s
	 * different creation strategies. As far as possible, we'll perform static
	 * type checking to avoid creation of the target bean.
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the type for the bean if determinable, or {@code null} otherwise
	 * @see #createBean
	 */
	@Nullable
	protected Class<?> getTypeForFactoryMethod(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		ResolvableType cachedReturnType = mbd.factoryMethodReturnType;
		if (cachedReturnType != null) {
			return cachedReturnType.resolve();
		}

		Class<?> factoryClass;
		boolean isStatic = true;

		String factoryBeanName = mbd.getFactoryBeanName();
		if (factoryBeanName != null) {
			if (factoryBeanName.equals(beanName)) {
				throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
						"factory-bean reference points back to the same bean definition");
			}
			// Check declared factory method return type on factory class.
			factoryClass = getType(factoryBeanName);
			isStatic = false;
		}
		else {
			// Check declared factory method return type on bean class.
			factoryClass = resolveBeanClass(mbd, beanName, typesToMatch);
		}

		if (factoryClass == null) {
			return null;
		}
		factoryClass = ClassUtils.getUserClass(factoryClass);

		// If all factory methods have the same return type, return that type.
		// Can't clearly figure out exact method due to type converting / autowiring!
		Class<?> commonType = null;
		Method uniqueCandidate = null;
		int minNrOfArgs =
				(mbd.hasConstructorArgumentValues() ? mbd.getConstructorArgumentValues().getArgumentCount() : 0);
		Method[] candidates = this.factoryMethodCandidateCache.computeIfAbsent(
				factoryClass, ReflectionUtils::getUniqueDeclaredMethods);

		for (Method candidate : candidates) {
			if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate) &&
					candidate.getParameterCount() >= minNrOfArgs) {
				// Declared type variables to inspect?
				if (candidate.getTypeParameters().length > 0) {
					try {
						// Fully resolve parameter names and argument values.
						Class<?>[] paramTypes = candidate.getParameterTypes();
						String[] paramNames = null;
						ParameterNameDiscoverer pnd = getParameterNameDiscoverer();
						if (pnd != null) {
							paramNames = pnd.getParameterNames(candidate);
						}
						ConstructorArgumentValues cav = mbd.getConstructorArgumentValues();
						Set<ConstructorArgumentValues.ValueHolder> usedValueHolders = new HashSet<>(paramTypes.length);
						Object[] args = new Object[paramTypes.length];
						for (int i = 0; i < args.length; i++) {
							ConstructorArgumentValues.ValueHolder valueHolder = cav.getArgumentValue(
									i, paramTypes[i], (paramNames != null ? paramNames[i] : null), usedValueHolders);
							if (valueHolder == null) {
								valueHolder = cav.getGenericArgumentValue(null, null, usedValueHolders);
							}
							if (valueHolder != null) {
								args[i] = valueHolder.getValue();
								usedValueHolders.add(valueHolder);
							}
						}
						Class<?> returnType = AutowireUtils.resolveReturnTypeForFactoryMethod(
								candidate, args, getBeanClassLoader());
						uniqueCandidate = (commonType == null && returnType == candidate.getReturnType() ?
								candidate : null);
						commonType = ClassUtils.determineCommonAncestor(returnType, commonType);
						if (commonType == null) {
							// Ambiguous return types found: return null to indicate "not determinable".
							return null;
						}
					}
					catch (Throwable ex) {
						if (logger.isDebugEnabled()) {
							logger.debug("Failed to resolve generic return type for factory method: " + ex);
						}
					}
				}
				else {
					uniqueCandidate = (commonType == null ? candidate : null);
					commonType = ClassUtils.determineCommonAncestor(candidate.getReturnType(), commonType);
					if (commonType == null) {
						// Ambiguous return types found: return null to indicate "not determinable".
						return null;
					}
				}
			}
		}

		mbd.factoryMethodToIntrospect = uniqueCandidate;
		if (commonType == null) {
			return null;
		}
		// Common return type found: all factory methods return same type. For a non-parameterized
		// unique candidate, cache the full type declaration context of the target factory method.
		cachedReturnType = (uniqueCandidate != null ?
				ResolvableType.forMethodReturnType(uniqueCandidate) : ResolvableType.forClass(commonType));
		mbd.factoryMethodReturnType = cachedReturnType;
		return cachedReturnType.resolve();
	}

	/**
	 * This implementation attempts to query the FactoryBean's generic parameter metadata
	 * if present to determine the object type. If not present, i.e. the FactoryBean is
	 * declared as a raw type, checks the FactoryBean's {@code getObjectType} method
	 * on a plain instance of the FactoryBean, without bean properties applied yet.
	 * If this doesn't return a type yet, a full creation of the FactoryBean is
	 * used as fallback (through delegation to the superclass's implementation).
	 * <p>The shortcut check for a FactoryBean is only applied in case of a singleton
	 * FactoryBean. If the FactoryBean instance itself is not kept as singleton,
	 * it will be fully created to check the type of its exposed object.
	 */
	@Override
	@Nullable
	protected Class<?> getTypeForFactoryBean(String beanName, RootBeanDefinition mbd) {
		if (mbd.getInstanceSupplier() != null) {
			ResolvableType targetType = mbd.targetType;
			if (targetType != null) {
				Class<?> result = targetType.as(FactoryBean.class).getGeneric().resolve();
				if (result != null) {
					return result;
				}
			}
			if (mbd.hasBeanClass()) {
				Class<?> result = GenericTypeResolver.resolveTypeArgument(mbd.getBeanClass(), FactoryBean.class);
				if (result != null) {
					return result;
				}
			}
		}

		String factoryBeanName = mbd.getFactoryBeanName();
		String factoryMethodName = mbd.getFactoryMethodName();

		if (factoryBeanName != null) {
			if (factoryMethodName != null) {
				// Try to obtain the FactoryBean's object type from its factory method declaration
				// without instantiating the containing bean at all.
				BeanDefinition fbDef = getBeanDefinition(factoryBeanName);
				if (fbDef instanceof AbstractBeanDefinition) {
					AbstractBeanDefinition afbDef = (AbstractBeanDefinition) fbDef;
					if (afbDef.hasBeanClass()) {
						Class<?> result = getTypeForFactoryBeanFromMethod(afbDef.getBeanClass(), factoryMethodName);
						if (result != null) {
							return result;
						}
					}
				}
			}
			// If not resolvable above and the referenced factory bean doesn't exist yet,
			// exit here - we don't want to force the creation of another bean just to
			// obtain a FactoryBean's object type...
			if (!isBeanEligibleForMetadataCaching(factoryBeanName)) {
				return null;
			}
		}

		// Let's obtain a shortcut instance for an early getObjectType() call...
		FactoryBean<?> fb = (mbd.isSingleton() ?
				getSingletonFactoryBeanForTypeCheck(beanName, mbd) :
				getNonSingletonFactoryBeanForTypeCheck(beanName, mbd));

		if (fb != null) {
			// Try to obtain the FactoryBean's object type from this early stage of the instance.
			Class<?> result = getTypeForFactoryBean(fb);
			if (result != null) {
				return result;
			}
			else {
				// No type found for shortcut FactoryBean instance:
				// fall back to full creation of the FactoryBean instance.
				return super.getTypeForFactoryBean(beanName, mbd);
			}
		}

		if (factoryBeanName == null && mbd.hasBeanClass()) {
			// No early bean instantiation possible: determine FactoryBean's type from
			// static factory method signature or from class inheritance hierarchy...
			if (factoryMethodName != null) {
				return getTypeForFactoryBeanFromMethod(mbd.getBeanClass(), factoryMethodName);
			}
			else {
				return GenericTypeResolver.resolveTypeArgument(mbd.getBeanClass(), FactoryBean.class);
			}
		}

		return null;
	}

	/**
	 * Introspect the factory method signatures on the given bean class,
	 * trying to find a common {@code FactoryBean} object type declared there.
	 * @param beanClass the bean class to find the factory method on
	 * @param factoryMethodName the name of the factory method
	 * @return the common {@code FactoryBean} object type, or {@code null} if none
	 */
	@Nullable
	private Class<?> getTypeForFactoryBeanFromMethod(Class<?> beanClass, final String factoryMethodName) {

		/**
		 * Holder used to keep a reference to a {@code Class} value.
		 */
		class Holder {

			@Nullable
			Class<?> value = null;
		}

		final Holder objectType = new Holder();

		// CGLIB subclass methods hide generic parameters; look at the original user class.
		Class<?> fbClass = ClassUtils.getUserClass(beanClass);

		// Find the given factory method, taking into account that in the case of
		// @Bean methods, there may be parameters present.
		ReflectionUtils.doWithMethods(fbClass, method -> {
			if (method.getName().equals(factoryMethodName) &&
					FactoryBean.class.isAssignableFrom(method.getReturnType())) {
				Class<?> currentType = GenericTypeResolver.resolveReturnTypeArgument(method, FactoryBean.class);
				if (currentType != null) {
					objectType.value = ClassUtils.determineCommonAncestor(currentType, objectType.value);
				}
			}
		});

		return (objectType.value != null && Object.class != objectType.value ? objectType.value : null);
	}

	/**
	 * Obtain a reference for early access to the specified bean,
	 * typically for the purpose of resolving a circular reference.
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param bean the raw bean instance
	 * @return the object to expose as bean reference
	 */
	protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object bean) {
		// 假设没有回调任何的后置处理器，那么exposedObject的实例就为bean实例
		// Tips：此时的这个Bean实例其实是仅仅创建完成而已，还没属性填充呢（这在后头）
		Object exposedObject = bean;
		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
					SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
					exposedObject = ibp.getEarlyBeanReference(exposedObject, beanName);
				}
			}
		}
		return exposedObject;
	}


	//---------------------------------------------------------------------
	// Implementation methods
	//---------------------------------------------------------------------

	/**
	 * Obtain a "shortcut" singleton FactoryBean instance to use for a
	 * {@code getObjectType()} call, without full initialization of the FactoryBean.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the FactoryBean instance, or {@code null} to indicate
	 * that we couldn't obtain a shortcut FactoryBean instance
	 */
	@Nullable
	private FactoryBean<?> getSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
		synchronized (getSingletonMutex()) {
			BeanWrapper bw = this.factoryBeanInstanceCache.get(beanName);
			if (bw != null) {
				return (FactoryBean<?>) bw.getWrappedInstance();
			}
			Object beanInstance = getSingleton(beanName, false);
			if (beanInstance instanceof FactoryBean) {
				return (FactoryBean<?>) beanInstance;
			}
			if (isSingletonCurrentlyInCreation(beanName) ||
					(mbd.getFactoryBeanName() != null && isSingletonCurrentlyInCreation(mbd.getFactoryBeanName()))) {
				return null;
			}

			Object instance;
			try {
				// Mark this bean as currently in creation, even if just partially.
				beforeSingletonCreation(beanName);
				// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
				instance = resolveBeforeInstantiation(beanName, mbd);
				if (instance == null) {
					bw = createBeanInstance(beanName, mbd, null);
					instance = bw.getWrappedInstance();
				}
			}
			catch (UnsatisfiedDependencyException ex) {
				// Don't swallow, probably misconfiguration...
				throw ex;
			}
			catch (BeanCreationException ex) {
				// Instantiation failure, maybe too early...
				if (logger.isDebugEnabled()) {
					logger.debug("Bean creation exception on singleton FactoryBean type check: " + ex);
				}
				onSuppressedException(ex);
				return null;
			}
			finally {
				// Finished partial creation of this bean.
				afterSingletonCreation(beanName);
			}

			FactoryBean<?> fb = getFactoryBean(beanName, instance);
			if (bw != null) {
				this.factoryBeanInstanceCache.put(beanName, bw);
			}
			return fb;
		}
	}

	/**
	 * Obtain a "shortcut" non-singleton FactoryBean instance to use for a
	 * {@code getObjectType()} call, without full initialization of the FactoryBean.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the FactoryBean instance, or {@code null} to indicate
	 * that we couldn't obtain a shortcut FactoryBean instance
	 */
	@Nullable
	private FactoryBean<?> getNonSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
		if (isPrototypeCurrentlyInCreation(beanName)) {
			return null;
		}

		Object instance;
		try {
			// Mark this bean as currently in creation, even if just partially.
			beforePrototypeCreation(beanName);
			// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
			instance = resolveBeforeInstantiation(beanName, mbd);
			if (instance == null) {
				BeanWrapper bw = createBeanInstance(beanName, mbd, null);
				instance = bw.getWrappedInstance();
			}
		}
		catch (UnsatisfiedDependencyException ex) {
			// Don't swallow, probably misconfiguration...
			throw ex;
		}
		catch (BeanCreationException ex) {
			// Instantiation failure, maybe too early...
			if (logger.isDebugEnabled()) {
				logger.debug("Bean creation exception on non-singleton FactoryBean type check: " + ex);
			}
			onSuppressedException(ex);
			return null;
		}
		finally {
			// Finished partial creation of this bean.
			afterPrototypeCreation(beanName);
		}

		return getFactoryBean(beanName, instance);
	}

	/**
	 * Apply MergedBeanDefinitionPostProcessors to the specified bean definition,
	 * invoking their {@code postProcessMergedBeanDefinition} methods.
	 * @param mbd the merged bean definition for the bean
	 * @param beanType the actual type of the managed bean instance
	 * @param beanName the name of the bean
	 * @see MergedBeanDefinitionPostProcessor#postProcessMergedBeanDefinition
	 */
	protected void applyMergedBeanDefinitionPostProcessors(RootBeanDefinition mbd, Class<?> beanType, String beanName) {
		for (BeanPostProcessor bp : getBeanPostProcessors()) {
			if (bp instanceof MergedBeanDefinitionPostProcessor) {
				MergedBeanDefinitionPostProcessor bdp = (MergedBeanDefinitionPostProcessor) bp;
				bdp.postProcessMergedBeanDefinition(mbd, beanType, beanName);
			}
		}
	}

	/**
	 * Apply before-instantiation post-processors, resolving whether there is a
	 * before-instantiation shortcut for the specified bean.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the shortcut-determined bean instance, or {@code null} if none
	 */
	@Nullable
	protected Object resolveBeforeInstantiation(String beanName, RootBeanDefinition mbd) {
		Object bean = null;
		if (!Boolean.FALSE.equals(mbd.beforeInstantiationResolved)) {
			// Make sure bean class is actually resolved at this point.
			if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
				// 获取给定的Bean所对应的目标类型（应该被创建出的类型）
				Class<?> targetType = determineTargetType(beanName, mbd);
				if (targetType != null) {
					// 依次回调Bean实例化前后置处理和Bean实例化后的后置处理器（如果Bean被实例化了的话）
					bean = applyBeanPostProcessorsBeforeInstantiation(targetType, beanName);
					if (bean != null) {
						bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);
					}
				}
			}
			mbd.beforeInstantiationResolved = (bean != null);
		}
		return bean;
	}

	/**
	 * Apply InstantiationAwareBeanPostProcessors to the specified bean definition
	 * (by class and name), invoking their {@code postProcessBeforeInstantiation} methods.
	 * <p>Any returned object will be used as the bean instead of actually instantiating
	 * the target bean. A {@code null} return value from the post-processor will
	 * result in the target bean being instantiated.
	 * @param beanClass the class of the bean to be instantiated
	 * @param beanName the name of the bean
	 * @return the bean object to use instead of a default instance of the target bean, or {@code null}
	 * @see InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation
	 */
	@Nullable
	protected Object applyBeanPostProcessorsBeforeInstantiation(Class<?> beanClass, String beanName) {
		for (BeanPostProcessor bp : getBeanPostProcessors()) {
			if (bp instanceof InstantiationAwareBeanPostProcessor) {
				InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
				Object result = ibp.postProcessBeforeInstantiation(beanClass, beanName);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}

	/**
	 * Create a new instance for the specified bean, using an appropriate instantiation strategy:
	 * factory method, constructor autowiring, or simple instantiation.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a BeanWrapper for the new instance
	 * @see #obtainFromSupplier
	 * @see #instantiateUsingFactoryMethod
	 * @see #autowireConstructor
	 * @see #instantiateBean
	 */
	protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, @Nullable Object[] args) {
		// Make sure bean class is actually resolved at this point.
		// 【步骤1】：获取当前Bean的实例类型
		Class<?> beanClass = resolveBeanClass(mbd, beanName);

		// 【步骤2】：校验这个Bean类型是否存在，以及是否为公有类型，且是否有公有构造器
		// 其实这主要是3个校验，从左往右，逐步细化，最终的目的还是判断能否通过构造器进行实例化
		if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
		}

		// 【步骤3】：通过回调第三方（如果有的话）来创建Bean实例
		Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
		if (instanceSupplier != null) {
			return obtainFromSupplier(instanceSupplier, beanName);
		}

		// 【步骤4】：如果在定义Bean的时候，声明了工厂方法，则通过工厂方法来创建Bean实例
		// FactoryMethodName对应的就是在Bean的定义中使用的factory-method属性
		// 此处的逻辑是：判断该Bean是否有定义factory-method，若定义了，
		// 则使用工厂方法来完成Bean的创建。
		if (mbd.getFactoryMethodName() != null) {
			return instantiateUsingFactoryMethod(beanName, mbd, args);
		}

		// Shortcut when re-creating the same bean...
		// 【步骤5】：根据构造函数的传参（类型、个数、顺序等）来解析出使用哪个构造函数
		// 变量resolved表示是否已经经过了解析并确定了构造函数
		boolean resolved = false;
		boolean autowireNecessary = false;
		if (args == null) {
			synchronized (mbd.constructorArgumentLock) {
				if (mbd.resolvedConstructorOrFactoryMethod != null) {
					// 如果实例变量resolvedConstructorOrFactoryMethod非空，
					// 表明已经解析出构造函数/工厂方法（即已经确定了使用哪个构造函数/工厂方法）
					// 为什么可能会出现已经解析了的情况呢？因为Spring为了提高性能，采用了缓存，
					// 即将已经解析出的构造函数或工厂方法给缓存下来，那么当下次遇到时就无需重复解析了。
					resolved = true;
					autowireNecessary = mbd.constructorArgumentsResolved;
				}
			}
		}
		// resolved为true，表明已经解析出应该使用哪个构造函数，因此此处直接使用已经解析出的构造函数即可
		if (resolved) {
			if (autowireNecessary) {
				return autowireConstructor(beanName, mbd, null, null);
			}
			else {
				return instantiateBean(beanName, mbd);
			}
		}

		// Candidate constructors for autowiring?
		// 运行到此处，表明前面并没有解析出该使用哪个构造函数，因此，接下来需要解析出所对应的构造函数
		// determineConstructorsFromBeanPostProcessors方法中是调用SmartInstantiationAwareBeanPostProcessor类型的后置处理器来让
		// 用户决定使用哪个构造函数（这也是一个扩展点，也就是说在由Spring自身来决定使用哪个构造函数前，
		// 给一个机会来让开发人员来决定，极大了增加了灵活性）
		Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);
		if (ctors != null || mbd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR ||
				mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args)) {
			return autowireConstructor(beanName, mbd, ctors, args);
		}

		// Preferred constructors for default construction?
		// 获取默认构造函数进行自动注入构建
		ctors = mbd.getPreferredConstructors();
		if (ctors != null) {
			return autowireConstructor(beanName, mbd, ctors, null);
		}

		// No special handling: simply use no-arg constructor.
		// 到了此处，没办法了，只有直接拿无参构造函数来进行实例创建了
		return instantiateBean(beanName, mbd);
	}

	/**
	 * Obtain a bean instance from the given supplier.
	 * @param instanceSupplier the configured supplier
	 * @param beanName the corresponding bean name
	 * @return a BeanWrapper for the new instance
	 * @since 5.0
	 * @see #getObjectForBeanInstance
	 */
	protected BeanWrapper obtainFromSupplier(Supplier<?> instanceSupplier, String beanName) {
		Object instance;

		String outerBean = this.currentlyCreatedBean.get();
		this.currentlyCreatedBean.set(beanName);
		try {
			instance = instanceSupplier.get();
		}
		finally {
			if (outerBean != null) {
				this.currentlyCreatedBean.set(outerBean);
			}
			else {
				this.currentlyCreatedBean.remove();
			}
		}

		if (instance == null) {
			instance = new NullBean();
		}
		BeanWrapper bw = new BeanWrapperImpl(instance);
		initBeanWrapper(bw);
		return bw;
	}

	/**
	 * Overridden in order to implicitly register the currently created bean as
	 * dependent on further beans getting programmatically retrieved during a
	 * {@link Supplier} callback.
	 * @since 5.0
	 * @see #obtainFromSupplier
	 */
	@Override
	protected Object getObjectForBeanInstance(
			Object beanInstance, String name, String beanName, @Nullable RootBeanDefinition mbd) {

		String currentlyCreatedBean = this.currentlyCreatedBean.get();
		if (currentlyCreatedBean != null) {
			registerDependentBean(beanName, currentlyCreatedBean);
		}

		return super.getObjectForBeanInstance(beanInstance, name, beanName, mbd);
	}

	/**
	 * Determine candidate constructors to use for the given bean, checking all registered
	 * {@link SmartInstantiationAwareBeanPostProcessor SmartInstantiationAwareBeanPostProcessors}.
	 * @param beanClass the raw class of the bean
	 * @param beanName the name of the bean
	 * @return the candidate constructors, or {@code null} if none specified
	 * @throws org.springframework.beans.BeansException in case of errors
	 * @see org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor#determineCandidateConstructors
	 */
	@Nullable
	protected Constructor<?>[] determineConstructorsFromBeanPostProcessors(@Nullable Class<?> beanClass, String beanName)
			throws BeansException {

		if (beanClass != null && hasInstantiationAwareBeanPostProcessors()) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
					SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
					Constructor<?>[] ctors = ibp.determineCandidateConstructors(beanClass, beanName);
					if (ctors != null) {
						return ctors;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Instantiate the given bean using its default constructor.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return a BeanWrapper for the new instance
	 */
	protected BeanWrapper instantiateBean(final String beanName, final RootBeanDefinition mbd) {
		try {
			Object beanInstance;
			final BeanFactory parent = this;
			// Java默认是不打开安全检查的，System.getSecurityManager()得到的是Null值，
			// 在不打开安全检查的前提下，本地程序拥有所有权限，
			// 但是当打开了安全检查，则需要使用doPrivileged来获取相应的权限
			if (System.getSecurityManager() != null) {
				// 通过doPrivileged方法的目的是获得更大的权限，可做到临时访问更多的资源
				beanInstance = AccessController.doPrivileged((PrivilegedAction<Object>) () ->
						getInstantiationStrategy().instantiate(mbd, beanName, parent),
						getAccessControlContext());
				// 这段逻辑等同与下面使用匿名内部类的写法：
				/**
				beanInstance = AccessController.doPrivileged(new PrivilegedAction<Object>() {
					@Override
					public Object run() {
						return getInstantiationStrategy().instantiate(mbd, beanName, parent);
					}
				}, getAccessControlContext());
				 */
			}
			else {
				// 没有打开安全检查，则无需通过doPrivileged，直接实例化策略进行实例化即可
				beanInstance = getInstantiationStrategy().instantiate(mbd, beanName, parent);
			}
			BeanWrapper bw = new BeanWrapperImpl(beanInstance);
			initBeanWrapper(bw);
			return bw;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Instantiation of bean failed", ex);
		}
	}

	/**
	 * Instantiate the bean using a named factory method. The method may be static, if the
	 * mbd parameter specifies a class, rather than a factoryBean, or an instance variable
	 * on a factory object itself configured using Dependency Injection.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param explicitArgs argument values passed in programmatically via the getBean method,
	 * or {@code null} if none (-> use constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 * @see #getBean(String, Object[])
	 */
	protected BeanWrapper instantiateUsingFactoryMethod(
			String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs) {

		return new ConstructorResolver(this).instantiateUsingFactoryMethod(beanName, mbd, explicitArgs);
	}

	/**
	 * "autowire constructor" (with constructor arguments by type) behavior.
	 * Also applied if explicit constructor argument values are specified,
	 * matching all remaining arguments with beans from the bean factory.
	 * <p>This corresponds to constructor injection: In this mode, a Spring
	 * bean factory is able to host components that expect constructor-based
	 * dependency resolution.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param ctors the chosen candidate constructors
	 * @param explicitArgs argument values passed in programmatically via the getBean method,
	 * or {@code null} if none (-> use constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 */
	protected BeanWrapper autowireConstructor(
			String beanName, RootBeanDefinition mbd, @Nullable Constructor<?>[] ctors, @Nullable Object[] explicitArgs) {

		return new ConstructorResolver(this).autowireConstructor(beanName, mbd, ctors, explicitArgs);
	}

	/**
	 * Populate the bean instance in the given BeanWrapper with the property values
	 * from the bean definition.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param bw the BeanWrapper with bean instance
	 */
	@SuppressWarnings("deprecation")  // for postProcessPropertyValues
	protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
		// 【步骤1】：判断Bean实例是否为空，且是否有需要注入的属性，并做针对性处理
		if (bw == null) {
			// 先判断下是否有属性值需要进行注入，若有则抛出异常，若没有则直接返回
			if (mbd.hasPropertyValues()) {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
			}
			else {
				// Skip property population phase for null instance.
				return;
			}
		}

		// 运行到此处，表明Bean实例非空（但是否有需要注入的属性值是不确定的）
		// Give any InstantiationAwareBeanPostProcessors the opportunity to modify the
		// state of the bean before properties are set. This can be used, for example,
		// to support styles of field injection.
		boolean continueWithPropertyPopulation = true;
		// 【步骤2】：给最后一次机会在Spring进行属性填充前让用户来改变Bean
		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof InstantiationAwareBeanPostProcessor) {
					InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
					// postProcessAfterInstantiation方法的返回值为是否继续填充Bean
					// 因为用户可能已经在postProcessAfterInstantiation方法中对Bean进行了填充
					if (!ibp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
						continueWithPropertyPopulation = false;
						break;
					}
				}
			}
		}

		// 如果在后置处理器postProcessAfterInstantiation中发出了停止填充的命令（即返回false）,
		// 则直接返回，不再进行后续的属性填充操作
		if (!continueWithPropertyPopulation) {
			return;
		}

		// 【步骤3】：Spring根据类型/名称自动进行属性值获取
		// 注意：获取到的属性值并没有被直接应用到当前Bean实例中，
		// 这也是给后面的后置处理器一个机会来改变属性值（注：是改变即将复制给Bean的属性值）
		// Tips:只有在XML配置文件中<bean/>标签中使用了autowire属性来指定注入类型（byName或byType）才会执行下面的autowireByName和autowireByType方法，
		// 对于使用注解@Autowire并不是在这里进行处理的。
		PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);

		if (mbd.getResolvedAutowireMode() == AUTOWIRE_BY_NAME || mbd.getResolvedAutowireMode() == AUTOWIRE_BY_TYPE) {
			MutablePropertyValues newPvs = new MutablePropertyValues(pvs);
			// Add property values based on autowire by name if applicable.
			if (mbd.getResolvedAutowireMode() == AUTOWIRE_BY_NAME) {
				autowireByName(beanName, mbd, bw, newPvs);
			}
			// Add property values based on autowire by type if applicable.
			if (mbd.getResolvedAutowireMode() == AUTOWIRE_BY_TYPE) {
				autowireByType(beanName, mbd, bw, newPvs);
			}
			pvs = newPvs;
		}

		// 【步骤4】：回调后置处理器/过滤器等进行属性值处理
		// 注：此时的属性值仍未赋值给Bean实例
		// Tips：上一步的autowireByName和autowireByType方法只是很一般化的逻辑，
		// 像对于@Autowire注解、@Resource注解的处理（包括相应依赖属性的处理），
		// 压根就不经过上面的autowireByName和autowireByType方法，
		// 而是通过InstantiationAwareBeanPostProcessor类型的后置处理器来完成
		boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();
		boolean needsDepCheck = (mbd.getDependencyCheck() != AbstractBeanDefinition.DEPENDENCY_CHECK_NONE);

		// filteredPds数组中持有的是待注入的属性的各种描述信息，比如名称、类型等
		PropertyDescriptor[] filteredPds = null;
		if (hasInstAwareBpps) {
			if (pvs == null) {
				pvs = mbd.getPropertyValues();
			}
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof InstantiationAwareBeanPostProcessor) {
					InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
					// postProcessProperties后置处理方法中对传入的pvs做处理，返回新的属性值
					// Tips：对于使用@Autowired注解进行依赖注入的处理是通过AutowiredAnnotationBeanPostProcessor后置处理的postProcessProperties方法进行的，
					// 也就是在下面这个位置进行的依赖注入处理，而并非前面的autowireByType方法。
					PropertyValues pvsToUse = ibp.postProcessProperties(pvs, bw.getWrappedInstance(), beanName);
					// Spring中的逻辑是：当用户已经对属性值做了处理，那Spring就觉得不需要再做属性值处理了
					if (pvsToUse == null) {
						if (filteredPds == null) {
							// filterPropertyDescriptorsForDependencyCheck方法中过滤掉那些不需要进行依赖性检查的属性描述信息，
							// 因此返回的filteredPds就是需要进行依赖性检查的属性描述信息
							filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
						}
						// postProcessPropertyValues后置处理方法与前面的postProcessProperties方法的作用是类似的，
						// 其实都是给一个机会来让用户可以在属性值被设置到Bean实例之前对属性值进行修改，
						// 这两个方法的区别在于postProcessPropertyValues方法的入参中多了属性描述入参（filteredPds），
						// 有了这个参数的话，可以了解到要注入的属性信息，其中@Required注解的处理就是在该后置处理方法中进行的校验。
						// @Required注解的后置处理放在此处也是合理的，
						// 因为诸如@Autowired注解的后置处理已经在上头的postProcessProperties方法中处理了，如果有值，那就不会执行到此处，
						// 也就无需进行@Required注解的后置处理了（毕竟都有值了，再处理就是多此一举了）。
						pvsToUse = ibp.postProcessPropertyValues(pvs, filteredPds, bw.getWrappedInstance(), beanName);
						if (pvsToUse == null) {
							return;
						}
					}
					pvs = pvsToUse;
				}
			}
		}
		// 【步骤5】：依赖检查校验
		if (needsDepCheck) {
			if (filteredPds == null) {
				filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
			}
			// 依赖检查校验
			checkDependencies(beanName, mbd, filteredPds, pvs);
		}

		// 【步骤6】：将属性值应用到Bean实例上
		if (pvs != null) {
			applyPropertyValues(beanName, mbd, bw, pvs);
		}
	}

	/**
	 * Fill in any missing property values with references to
	 * other beans in this factory if autowire is set to "byName".
	 * @param beanName the name of the bean we're wiring up.
	 * Useful for debugging messages; not used functionally.
	 * @param mbd bean definition to update through autowiring
	 * @param bw the BeanWrapper from which we can obtain information about the bean
	 * @param pvs the PropertyValues to register wired objects with
	 */
	protected void autowireByName(
			String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {
		// 【步骤1】：获取需要进行依赖注入的属性名称列表
		String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
		// 【步骤2】：遍历需要进行依赖注入的属性列表，然后去逐一获取需要注入的Bean实例，并加入到pvs中由其持有
		for (String propertyName : propertyNames) {
			if (containsBean(propertyName)) {
				// 递归获取（初始化）需要注入的Bean
				Object bean = getBean(propertyName);
				pvs.add(propertyName, bean);
				// 注册依赖
				// Tips:属性名propertyName会作为Key保存在dependentBeanMap中，
				// 其实可以理解为所依赖的属性会作为Key存在dependentBeanMap中，
				// 所以如果发生循环依赖，那么当前Bean的名称反而会作为Key存在（因为被别的Bean作为依赖属性了）。
				registerDependentBean(propertyName, beanName);
				if (logger.isTraceEnabled()) {
					logger.trace("Added autowiring by name from bean name '" + beanName +
							"' via property '" + propertyName + "' to bean named '" + propertyName + "'");
				}
			}
			else {
				if (logger.isTraceEnabled()) {
					logger.trace("Not autowiring property '" + propertyName + "' of bean '" + beanName +
							"' by name: no matching bean found");
				}
			}
		}
	}

	/**
	 * Abstract method defining "autowire by type" (bean properties by type) behavior.
	 * <p>This is like PicoContainer default, in which there must be exactly one bean
	 * of the property type in the bean factory. This makes bean factories simple to
	 * configure for small namespaces, but doesn't work as well as standard Spring
	 * behavior for bigger applications.
	 * @param beanName the name of the bean to autowire by type
	 * @param mbd the merged bean definition to update through autowiring
	 * @param bw the BeanWrapper from which we can obtain information about the bean
	 * @param pvs the PropertyValues to register wired objects with
	 */
	protected void autowireByType(
			String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

		// 【步骤1】：获取类型转换器实例
		TypeConverter converter = getCustomTypeConverter();
		if (converter == null) {
			// BeanWrapper也实现了TypeConverter接口的
			converter = bw;
		}

		Set<String> autowiredBeanNames = new LinkedHashSet<>(4);
		// 【步骤2】：获取需要依赖注入的属性
		String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
		// 【步骤3】：依次遍历需要依赖注入的属性，获取相应的对象实例
		for (String propertyName : propertyNames) {
			try {
				// 获取需注入的属性的描述信息
				PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
				// Don't try autowiring by type for type Object: never makes sense,
				// even if it technically is a unsatisfied, non-simple property.
				// 对于Object类型的属性不会进行依赖注入处理(因为Object可以对应任何对象属性，无法区分)
				if (Object.class != pd.getPropertyType()) {
					// 获取当前需注入的属性的setter方法的入参
					MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
					// Do not allow eager init for type matching in case of a prioritized post-processor.
					// 如果是优先后处理器，则不允许立即初始化类型匹配。?
					boolean eager = !PriorityOrdered.class.isInstance(bw.getWrappedInstance());
					// desc中含有被注入的属性的setter方法的信息
					DependencyDescriptor desc = new AutowireByTypeDependencyDescriptor(methodParam, eager);
					// 解析指定beanName的Bean的属性所匹配的值，并把解析到的属性名称存储在autowiredBeanNames中，
					// 当属性存在多个封装Bean时，如private List<A> aList;
					// 将会找到所有匹配A类型的bean并将其注入，此时autowiredBeanNames将保存有注入的多个依赖Bean的名称。
					// resolveDependency方法的返回值为被注入的属性的值
					Object autowiredArgument = resolveDependency(desc, beanName, autowiredBeanNames, converter);
					if (autowiredArgument != null) {
						pvs.add(propertyName, autowiredArgument);
					}
					for (String autowiredBeanName : autowiredBeanNames) {
						registerDependentBean(autowiredBeanName, beanName);
						if (logger.isTraceEnabled()) {
							logger.trace("Autowiring by type from bean name '" + beanName + "' via property '" +
									propertyName + "' to bean named '" + autowiredBeanName + "'");
						}
					}
					autowiredBeanNames.clear();
				}
			}
			catch (BeansException ex) {
				throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, propertyName, ex);
			}
		}
	}


	/**
	 * Return an array of non-simple bean properties that are unsatisfied.
	 * These are probably unsatisfied references to other beans in the
	 * factory. Does not include simple properties like primitives or Strings.
	 * @param mbd the merged bean definition the bean was created with
	 * @param bw the BeanWrapper the bean was created with
	 * @return an array of bean property names
	 * @see org.springframework.beans.BeanUtils#isSimpleProperty
	 */
	protected String[] unsatisfiedNonSimpleProperties(AbstractBeanDefinition mbd, BeanWrapper bw) {
		Set<String> result = new TreeSet<>();
		// 获取Bean定义信息中已经定义的属性值
		// 比如在当前的Bean中有个属性叫address，然后在<bean/>中定义了该属性的值（如address=xxx）,
		// 那么此处就是获取该属性的值
		PropertyValues pvs = mbd.getPropertyValues();
		// 获取当前Bean的所有属性字段的描述信息
		PropertyDescriptor[] pds = bw.getPropertyDescriptors();
		// 遍历当前字段的所有描述信息，获取需要进行依赖注入的属性，并将其放到result中返回
		// 筛选逻辑：1）必须得有setter方法
		//         2）那些指明了不需要进行依赖校验的排除掉（因为不需要依赖校验意味着不注入都可以）
		//         3）已经有属性值了的不需要
		//         4）排除掉属性类型为基本类型的
		for (PropertyDescriptor pd : pds) {
			if (pd.getWriteMethod() != null && !isExcludedFromDependencyCheck(pd) && !pvs.contains(pd.getName()) &&
					!BeanUtils.isSimpleProperty(pd.getPropertyType())) {
				result.add(pd.getName());
			}
		}
		return StringUtils.toStringArray(result);
	}

	/**
	 * Extract a filtered set of PropertyDescriptors from the given BeanWrapper,
	 * excluding ignored dependency types or properties defined on ignored dependency interfaces.
	 * @param bw the BeanWrapper the bean was created with
	 * @param cache whether to cache filtered PropertyDescriptors for the given bean Class
	 * @return the filtered PropertyDescriptors
	 * @see #isExcludedFromDependencyCheck
	 * @see #filterPropertyDescriptorsForDependencyCheck(org.springframework.beans.BeanWrapper)
	 */
	protected PropertyDescriptor[] filterPropertyDescriptorsForDependencyCheck(BeanWrapper bw, boolean cache) {
		PropertyDescriptor[] filtered = this.filteredPropertyDescriptorsCache.get(bw.getWrappedClass());
		if (filtered == null) {
			// filterPropertyDescriptorsForDependencyCheck方法中过滤掉那些不需要进行依赖性检查的属性，
			// 返回的filtered是需要进行依赖性检查的属性的描述
			filtered = filterPropertyDescriptorsForDependencyCheck(bw);
			if (cache) {
				// ConcurrentMap类的putIfAbsent方法的逻辑是：
				// 如果Key在Map中不存在，则进行K-V的保存；
				// 如果Key在Map中已经存在了，则不进行K-V的保存，直接返回原来的V
				PropertyDescriptor[] existing =
						this.filteredPropertyDescriptorsCache.putIfAbsent(bw.getWrappedClass(), filtered);
				if (existing != null) {
					filtered = existing;
				}
			}
		}
		return filtered;
	}

	/**
	 * Extract a filtered set of PropertyDescriptors from the given BeanWrapper,
	 * excluding ignored dependency types or properties defined on ignored dependency interfaces.
	 * @param bw the BeanWrapper the bean was created with
	 * @return the filtered PropertyDescriptors
	 * @see #isExcludedFromDependencyCheck
	 */
	protected PropertyDescriptor[] filterPropertyDescriptorsForDependencyCheck(BeanWrapper bw) {
		List<PropertyDescriptor> pds = new ArrayList<>(Arrays.asList(bw.getPropertyDescriptors()));
		// isExcludedFromDependencyCheck方法的作用：确定给定的bean属性是否被排除在依赖项检查之外，
		// 如果返回true，则表明该属性不需要进行依赖检查，从pds中剔除
		// 所谓的依赖检查，比如包括属性注入时非空校验等
		pds.removeIf(this::isExcludedFromDependencyCheck);
		// 此处返回带参数的toArray方法，因为其可以支持返回泛型数组，
		// 而该方法的入参是具体的数组，如果能明确的知道数组的大小的话可以预先写上size，
		// 但也可以像下面这样处理，让toArray方法来决定实际分配的大小
		return pds.toArray(new PropertyDescriptor[0]);
	}

	/**
	 * Determine whether the given bean property is excluded from dependency checks.
	 * <p>This implementation excludes properties defined by CGLIB and
	 * properties whose type matches an ignored dependency type or which
	 * are defined by an ignored dependency interface.
	 * @param pd the PropertyDescriptor of the bean property
	 * @return whether the bean property is excluded
	 * @see #ignoreDependencyType(Class)
	 * @see #ignoreDependencyInterface(Class)
	 */
	protected boolean isExcludedFromDependencyCheck(PropertyDescriptor pd) {
		return (AutowireUtils.isExcludedFromDependencyCheck(pd) ||
				this.ignoredDependencyTypes.contains(pd.getPropertyType()) ||
				AutowireUtils.isSetterDefinedInInterface(pd, this.ignoredDependencyInterfaces));
	}

	/**
	 * Perform a dependency check that all properties exposed have been set,
	 * if desired. Dependency checks can be objects (collaborating beans),
	 * simple (primitives and String), or all (both).
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition the bean was created with
	 * @param pds the relevant property descriptors for the target bean
	 * @param pvs the property values to be applied to the bean
	 * @see #isExcludedFromDependencyCheck(java.beans.PropertyDescriptor)
	 */
	protected void checkDependencies(
			String beanName, AbstractBeanDefinition mbd, PropertyDescriptor[] pds, @Nullable PropertyValues pvs)
			throws UnsatisfiedDependencyException {

		int dependencyCheck = mbd.getDependencyCheck();
		for (PropertyDescriptor pd : pds) {
			if (pd.getWriteMethod() != null && (pvs == null || !pvs.contains(pd.getName()))) {
				boolean isSimple = BeanUtils.isSimpleProperty(pd.getPropertyType());
				boolean unsatisfied = (dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_ALL) ||
						(isSimple && dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_SIMPLE) ||
						(!isSimple && dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_OBJECTS);
				if (unsatisfied) {
					throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, pd.getName(),
							"Set this property value or disable dependency checking for this bean.");
				}
			}
		}
	}

	/**
	 * Apply the given property values, resolving any runtime references
	 * to other beans in this bean factory. Must use deep copy, so we
	 * don't permanently modify this property.
	 * @param beanName the bean name passed for better exception information
	 * @param mbd the merged bean definition
	 * @param bw the BeanWrapper wrapping the target object
	 * @param pvs the new property values
	 */
	protected void applyPropertyValues(String beanName, BeanDefinition mbd, BeanWrapper bw, PropertyValues pvs) {
		if (pvs.isEmpty()) {
			return;
		}

		if (System.getSecurityManager() != null && bw instanceof BeanWrapperImpl) {
			((BeanWrapperImpl) bw).setSecurityContext(getAccessControlContext());
		}

		MutablePropertyValues mpvs = null;
		List<PropertyValue> original;

		// 【步骤1】：先判断当前的属性值是否为已经转换过的，若是则直接赋值，否则先保存其原始值
		if (pvs instanceof MutablePropertyValues) {
			mpvs = (MutablePropertyValues) pvs;
			// 如果属性值已经转换完成不再需要进行转换的话，则isConverted方法返回true，否则返回false
			if (mpvs.isConverted()) {
				// Shortcut: use the pre-converted values as-is.
				try {
					// 对于已经完成转换的值，直接设置即可。
					bw.setPropertyValues(mpvs);
					return;
				}
				catch (BeansException ex) {
					throw new BeanCreationException(
							mbd.getResourceDescription(), beanName, "Error setting property values", ex);
				}
			}
			// 否则，获取未经转换的原始值
			original = mpvs.getPropertyValueList();
		}
		else {
			original = Arrays.asList(pvs.getPropertyValues());
		}
		// 运行到此处，表明属性值都尚未经过类型转换，比如"2"应该转换为整型2

		// 【步骤2】：获取用户类型转换解析的值解析器
		// 获取类型转换器
		TypeConverter converter = getCustomTypeConverter();
		if (converter == null) {
			converter = bw;
		}
		// 获取值解析器
		BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this, beanName, mbd, converter);

		// 【步骤3】：完成值类型转换
		// Create a deep copy, resolving any references for values.
		List<PropertyValue> deepCopy = new ArrayList<>(original.size());
		boolean resolveNecessary = false;
		for (PropertyValue pv : original) {
			if (pv.isConverted()) {
				deepCopy.add(pv);
			}
			else {
				String propertyName = pv.getName();
				// 原始值
				Object originalValue = pv.getValue();
				// 解析后的值
				Object resolvedValue = valueResolver.resolveValueIfNecessary(pv, originalValue);
				// 将经过解析后的值直接作为转换后的值
				Object convertedValue = resolvedValue;
				// 但还是要判断下是否需要转换，如果需要转换的化，还是要对resolvedValue进行转换
				boolean convertible = bw.isWritableProperty(propertyName) &&
						!PropertyAccessorUtils.isNestedOrIndexedProperty(propertyName);
				if (convertible) {
					convertedValue = convertForProperty(resolvedValue, propertyName, bw, converter);
				}
				// Possibly store converted value in merged bean definition,
				// in order to avoid re-conversion for every created bean instance.
				if (resolvedValue == originalValue) {
					if (convertible) {
						pv.setConvertedValue(convertedValue);
					}
					deepCopy.add(pv);
				}
				else if (convertible && originalValue instanceof TypedStringValue &&
						!((TypedStringValue) originalValue).isDynamic() &&
						!(convertedValue instanceof Collection || ObjectUtils.isArray(convertedValue))) {
					pv.setConvertedValue(convertedValue);
					deepCopy.add(pv);
				}
				else {
					resolveNecessary = true;
					deepCopy.add(new PropertyValue(pv, convertedValue));
				}
			}
		}
		if (mpvs != null && !resolveNecessary) {
			mpvs.setConverted();
		}

		// Set our (possibly massaged) deep copy.
		// 【步骤4】：完成属性值赋值
		try {
			bw.setPropertyValues(new MutablePropertyValues(deepCopy));
		}
		catch (BeansException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Error setting property values", ex);
		}
	}

	/**
	 * Convert the given value for the specified target property.
	 */
	@Nullable
	private Object convertForProperty(
			@Nullable Object value, String propertyName, BeanWrapper bw, TypeConverter converter) {

		if (converter instanceof BeanWrapperImpl) {
			return ((BeanWrapperImpl) converter).convertForProperty(value, propertyName);
		}
		else {
			PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
			MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
			return converter.convertIfNecessary(value, pd.getPropertyType(), methodParam);
		}
	}


	/**
	 * Initialize the given bean instance, applying factory callbacks
	 * as well as init methods and bean post processors.
	 * <p>Called from {@link #createBean} for traditionally defined beans,
	 * and from {@link #initializeBean} for existing bean instances.
	 * @param beanName the bean name in the factory (for debugging purposes)
	 * @param bean the new bean instance we may need to initialize
	 * @param mbd the bean definition that the bean was created with
	 * (can also be {@code null}, if given an existing bean instance)
	 * @return the initialized bean instance (potentially wrapped)
	 * @see BeanNameAware
	 * @see BeanClassLoaderAware
	 * @see BeanFactoryAware
	 * @see #applyBeanPostProcessorsBeforeInitialization
	 * @see #invokeInitMethods
	 * @see #applyBeanPostProcessorsAfterInitialization
	 */
	protected Object initializeBean(final String beanName, final Object bean, @Nullable RootBeanDefinition mbd) {
		// 【步骤1】：回调相应的Aware接口实现类的方法
		// 主要是BeanNameAware、BeanClassLoaderAware和BeanFactoryAware
		if (System.getSecurityManager() != null) {
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				invokeAwareMethods(beanName, bean);
				return null;
			}, getAccessControlContext());
		}
		else {
			invokeAwareMethods(beanName, bean);
		}

		// 【步骤2】：回调初始化前的后置处理器postProcessBeforeInitialization
		Object wrappedBean = bean;
		if (mbd == null || !mbd.isSynthetic()) {
			wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
		}

		// 【步骤3】：回调相应的初始方法
		try {
			invokeInitMethods(beanName, wrappedBean, mbd);
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					(mbd != null ? mbd.getResourceDescription() : null),
					beanName, "Invocation of init method failed", ex);
		}
		// 【步骤4】：在回调了初始化方法之后，回调初始化后的后置处理器postProcessAfterInitialization
		if (mbd == null || !mbd.isSynthetic()) {
			wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
		}

		return wrappedBean;
	}

	private void invokeAwareMethods(final String beanName, final Object bean) {
		if (bean instanceof Aware) {
			if (bean instanceof BeanNameAware) {
				((BeanNameAware) bean).setBeanName(beanName);
			}
			if (bean instanceof BeanClassLoaderAware) {
				ClassLoader bcl = getBeanClassLoader();
				if (bcl != null) {
					((BeanClassLoaderAware) bean).setBeanClassLoader(bcl);
				}
			}
			if (bean instanceof BeanFactoryAware) {
				((BeanFactoryAware) bean).setBeanFactory(AbstractAutowireCapableBeanFactory.this);
			}
		}
	}

	/**
	 * Give a bean a chance to react now all its properties are set,
	 * and a chance to know about its owning bean factory (this object).
	 * This means checking whether the bean implements InitializingBean or defines
	 * a custom init method, and invoking the necessary callback(s) if it does.
	 * @param beanName the bean name in the factory (for debugging purposes)
	 * @param bean the new bean instance we may need to initialize
	 * @param mbd the merged bean definition that the bean was created with
	 * (can also be {@code null}, if given an existing bean instance)
	 * @throws Throwable if thrown by init methods or by the invocation process
	 * @see #invokeCustomInitMethod
	 */
	protected void invokeInitMethods(String beanName, final Object bean, @Nullable RootBeanDefinition mbd)
			throws Throwable {

		boolean isInitializingBean = (bean instanceof InitializingBean);
		if (isInitializingBean && (mbd == null || !mbd.isExternallyManagedInitMethod("afterPropertiesSet"))) {
			if (logger.isTraceEnabled()) {
				logger.trace("Invoking afterPropertiesSet() on bean with name '" + beanName + "'");
			}
			if (System.getSecurityManager() != null) {
				try {
					AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> {
						((InitializingBean) bean).afterPropertiesSet();
						return null;
					}, getAccessControlContext());
				}
				catch (PrivilegedActionException pae) {
					throw pae.getException();
				}
			}
			else {
				((InitializingBean) bean).afterPropertiesSet();
			}
		}

		if (mbd != null && bean.getClass() != NullBean.class) {
			String initMethodName = mbd.getInitMethodName();
			if (StringUtils.hasLength(initMethodName) &&
					!(isInitializingBean && "afterPropertiesSet".equals(initMethodName)) &&
					!mbd.isExternallyManagedInitMethod(initMethodName)) {
				invokeCustomInitMethod(beanName, bean, mbd);
			}
		}
	}

	/**
	 * Invoke the specified custom init method on the given bean.
	 * Called by invokeInitMethods.
	 * <p>Can be overridden in subclasses for custom resolution of init
	 * methods with arguments.
	 * @see #invokeInitMethods
	 */
	protected void invokeCustomInitMethod(String beanName, final Object bean, RootBeanDefinition mbd)
			throws Throwable {

		String initMethodName = mbd.getInitMethodName();
		Assert.state(initMethodName != null, "No init method set");
		Method initMethod = (mbd.isNonPublicAccessAllowed() ?
				BeanUtils.findMethod(bean.getClass(), initMethodName) :
				ClassUtils.getMethodIfAvailable(bean.getClass(), initMethodName));

		if (initMethod == null) {
			if (mbd.isEnforceInitMethod()) {
				throw new BeanDefinitionValidationException("Could not find an init method named '" +
						initMethodName + "' on bean with name '" + beanName + "'");
			}
			else {
				if (logger.isTraceEnabled()) {
					logger.trace("No default init method named '" + initMethodName +
							"' found on bean with name '" + beanName + "'");
				}
				// Ignore non-existent default lifecycle methods.
				return;
			}
		}

		if (logger.isTraceEnabled()) {
			logger.trace("Invoking init method  '" + initMethodName + "' on bean with name '" + beanName + "'");
		}
		Method methodToInvoke = ClassUtils.getInterfaceMethodIfPossible(initMethod);

		if (System.getSecurityManager() != null) {
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				ReflectionUtils.makeAccessible(methodToInvoke);
				return null;
			});
			try {
				AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () ->
						methodToInvoke.invoke(bean), getAccessControlContext());
			}
			catch (PrivilegedActionException pae) {
				InvocationTargetException ex = (InvocationTargetException) pae.getException();
				throw ex.getTargetException();
			}
		}
		else {
			try {
				ReflectionUtils.makeAccessible(initMethod);
				initMethod.invoke(bean);
			}
			catch (InvocationTargetException ex) {
				throw ex.getTargetException();
			}
		}
	}


	/**
	 * Applies the {@code postProcessAfterInitialization} callback of all
	 * registered BeanPostProcessors, giving them a chance to post-process the
	 * object obtained from FactoryBeans (for example, to auto-proxy them).
	 * @see #applyBeanPostProcessorsAfterInitialization
	 */
	@Override
	protected Object postProcessObjectFromFactoryBean(Object object, String beanName) {
		return applyBeanPostProcessorsAfterInitialization(object, beanName);
	}

	/**
	 * Overridden to clear FactoryBean instance cache as well.
	 */
	@Override
	protected void removeSingleton(String beanName) {
		synchronized (getSingletonMutex()) {
			super.removeSingleton(beanName);
			this.factoryBeanInstanceCache.remove(beanName);
		}
	}

	/**
	 * Overridden to clear FactoryBean instance cache as well.
	 */
	@Override
	protected void clearSingletonCache() {
		synchronized (getSingletonMutex()) {
			super.clearSingletonCache();
			this.factoryBeanInstanceCache.clear();
		}
	}

	/**
	 * Expose the logger to collaborating delegates.
	 * @since 5.0.7
	 */
	Log getLogger() {
		return logger;
	}


	/**
	 * Special DependencyDescriptor variant for Spring's good old autowire="byType" mode.
	 * Always optional; never considering the parameter name for choosing a primary candidate.
	 */
	@SuppressWarnings("serial")
	private static class AutowireByTypeDependencyDescriptor extends DependencyDescriptor {

		public AutowireByTypeDependencyDescriptor(MethodParameter methodParameter, boolean eager) {
			super(methodParameter, false, eager);
		}

		@Override
		public String getDependencyName() {
			return null;
		}
	}

}
