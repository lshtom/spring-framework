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

package org.springframework.web.method.annotation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.core.Conventions;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpSessionRequiredException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Assist with initialization of the {@link Model} before controller method
 * invocation and with updates to it after the invocation.
 *
 * <p>On initialization the model is populated with attributes temporarily stored
 * in the session and through the invocation of {@code @ModelAttribute} methods.
 *
 * <p>On update model attributes are synchronized with the session and also
 * {@link BindingResult} attributes are added if missing.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public final class ModelFactory {

	private static final Log logger = LogFactory.getLog(ModelFactory.class);

	// modelMethods中持有当前HandlerMethod所在Bean类下所有被@ModelAttribute注解所标注的方法
	private final List<ModelMethod> modelMethods = new ArrayList<>();

	private final WebDataBinderFactory dataBinderFactory;

	private final SessionAttributesHandler sessionAttributesHandler;


	/**
	 * Create a new instance with the given {@code @ModelAttribute} methods.
	 * @param handlerMethods the {@code @ModelAttribute} methods to invoke
	 * @param binderFactory for preparation of {@link BindingResult} attributes
	 * @param attributeHandler for access to session attributes
	 */
	public ModelFactory(@Nullable List<InvocableHandlerMethod> handlerMethods,
			WebDataBinderFactory binderFactory, SessionAttributesHandler attributeHandler) {

		if (handlerMethods != null) {
			for (InvocableHandlerMethod handlerMethod : handlerMethods) {
				this.modelMethods.add(new ModelMethod(handlerMethod));
			}
		}
		this.dataBinderFactory = binderFactory;
		this.sessionAttributesHandler = attributeHandler;
	}


	/**
	 * Populate the model in the following order:
	 * <ol>
	 * <li>Retrieve "known" session attributes listed as {@code @SessionAttributes}.
	 * <li>Invoke {@code @ModelAttribute} methods
	 * <li>Find {@code @ModelAttribute} method arguments also listed as
	 * {@code @SessionAttributes} and ensure they're present in the model raising
	 * an exception if necessary.
	 * </ol>
	 * @param request the current request
	 * @param container a container with the model to be initialized
	 * @param handlerMethod the method for which the model is initialized
	 * @throws Exception may arise from {@code @ModelAttribute} methods
	 */
	public void initModel(NativeWebRequest request, ModelAndViewContainer container, HandlerMethod handlerMethod)
			throws Exception {

		// 【步骤1】：获取@SessionAttributes注解所定义的属性的属性值，并合并到mavContainer中。
		// 说明：实例变量sessionAttributesHandler中所持有的SessionAttributesHandler对象实例是来自于ModelFactory的构造方法传入，
		// 而其又是来自于getSessionAttributesHandler(handlerMethod)方法的返回，
		// 也就是说当前拿到的这个SessionAttributesHandler对象实例其实是与handlerMethod高度绑定的，
		// 而retrieveAttributes方法中要传入参数request，那是因为属性值是存在Session中，需要通过Request去获取。
		Map<String, ?> sessionAttributes = this.sessionAttributesHandler.retrieveAttributes(request);
		container.mergeAttributes(sessionAttributes);

		// 【步骤2】：执行标注了@ModelAttribute注解的方法，并将获取到的返回值给设置到Model中，而属性值所对应的属性名来自于@ModelAttribute注解的指定
		invokeModelAttributeMethods(request, container);

		// 说明：@ModelAttribute注解用于指定从Model中获取哪些属性的属性值（当该注解用于方法的入参前就属于这种情形），
		// 亦或者用于向Model中指定的属性写入相应的属性值（当该注解用于方法上时就属于这种情形），
		// 而属性值的获取就通过调用当前HandlerMethod所在的Bean类下被@ModelAttribute注解所标注的方法来拿到；
		// @SessionAttributes注解用于指定Model中的哪些属性要被缓存到Session中；
		// @SessionAttributes注解与@ModelAttribute注解之间并没有必然的联系，当然两者可以配合使用。

		// 【步骤3】：判断当前handlerMethod的入参是否既被@ModelAttribute注解中的属性所声明了，
		// 且又被在@SessionAttributes注解中的属性所声明，且还不在mavContainer中，
		// 那么将从SessionAttributes中获取并设置到mavContainer中。
		for (String name : findSessionAttributeArguments(handlerMethod)) {
			if (!container.containsAttribute(name)) {
				// 从sessionAttributesHandler中获取@SessionAttributes中所指定的属性的属性值
				Object value = this.sessionAttributesHandler.retrieveAttribute(request, name);
				if (value == null) {
					throw new HttpSessionRequiredException("Expected session attribute '" + name + "'", name);
				}
				// 然后将该属性值设置到相应的Model中
				container.addAttribute(name, value);
			}
		}
		// 说明：上面这个在方法的参数上既标注了@SessionAttributes注解又标注了@ModelAttribute注解意味着：
		// 从Session中获取指定属性的属性值，然后设置到Model中，
		// 上面的这步骤3的就是进行这种情形的处理的。
	}

	/**
	 * Invoke model attribute methods to populate the model.
	 * Attributes are added only if not already present in the model.
	 */
	private void invokeModelAttributeMethods(NativeWebRequest request, ModelAndViewContainer container)
			throws Exception {

		// 依次遍历当前HandlerMethod所在Bean类下所有被@ModelAttribute注解标注的方法进行调用处理
		while (!this.modelMethods.isEmpty()) {
			// 【步骤1】：获取标注了@ModelAttribute注解的方法
			InvocableHandlerMethod modelMethod = getNextModelMethod(container).getHandlerMethod();
			// 获取该方法上的注解信息
			ModelAttribute ann = modelMethod.getMethodAnnotation(ModelAttribute.class);
			Assert.state(ann != null, "No ModelAttribute annotation");
			// 如果属性名已经在mavContainer上则表明该方法已经被调用过了，直接跳过。
			if (container.containsAttribute(ann.name())) {
				if (!ann.binding()) {
					// 设置禁用数据绑定
					container.setBindingDisabled(ann.name());
				}
				continue;
			}

			// 【步骤2】：反射调用这个被@ModelAttribute注解所标注的方法并获取返回值
			Object returnValue = modelMethod.invokeForRequest(request, container);
			// 【步骤3】：处理返回值，设置到Model中
			if (!modelMethod.isVoid()){
				// 获取要设置到Model中的属性名
				String returnValueName = getNameForReturnValue(returnValue, modelMethod.getReturnType());
				if (!ann.binding()) {
					container.setBindingDisabled(returnValueName);
				}
				if (!container.containsAttribute(returnValueName)) {
					// mavContainer中持有Model，此处的addAttribute方法其实是将属性值添加到Model中
					container.addAttribute(returnValueName, returnValue);
				}
			}
		}
	}

	private ModelMethod getNextModelMethod(ModelAndViewContainer container) {
		for (ModelMethod modelMethod : this.modelMethods) {
			if (modelMethod.checkDependencies(container)) {
				this.modelMethods.remove(modelMethod);
				return modelMethod;
			}
		}
		ModelMethod modelMethod = this.modelMethods.get(0);
		this.modelMethods.remove(modelMethod);
		return modelMethod;
	}

	/**
	 * Find {@code @ModelAttribute} arguments also listed as {@code @SessionAttributes}.
	 */
	private List<String> findSessionAttributeArguments(HandlerMethod handlerMethod) {
		List<String> result = new ArrayList<>();
		// 遍历当前HandlerMethod的每一个参数，并依次判断该参数上是否标注了@ModelAttribute注解
		for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
			if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
				String name = getNameForParameter(parameter);
				Class<?> paramType = parameter.getParameterType();
				if (this.sessionAttributesHandler.isHandlerSessionAttribute(name, paramType)) {
					result.add(name);
				}
			}
		}
		return result;
	}

	/**
	 * Promote model attributes listed as {@code @SessionAttributes} to the session.
	 * Add {@link BindingResult} attributes where necessary.
	 * @param request the current request
	 * @param container contains the model to update
	 * @throws Exception if creating BindingResult attributes fails
	 */
	public void updateModel(NativeWebRequest request, ModelAndViewContainer container) throws Exception {
		ModelMap defaultModel = container.getDefaultModel();
		// 【步骤1】：管理维护SessionAttributes中的数据
		if (container.getSessionStatus().isComplete()){
			this.sessionAttributesHandler.cleanupAttributes(request);
		}
		else {
			// 这个的本质逻辑其实是利用Model的数据去更新SessionAttributes的数据
			this.sessionAttributesHandler.storeAttributes(request, defaultModel);
		}
		// 【步骤2】：给Model中需要的参数设置BindingResult，以备视图使用
		if (!container.isRequestHandled() && container.getModel() == defaultModel) {
			updateBindingResult(request, defaultModel);
		}
	}

	/**
	 * Add {@link BindingResult} attributes to the model for attributes that require it.
	 */
	private void updateBindingResult(NativeWebRequest request, ModelMap model) throws Exception {
		List<String> keyNames = new ArrayList<>(model.keySet());
		for (String name : keyNames) {
			Object value = model.get(name);
			// isBindingCandidate方法是判断是否需要为当前所遍历到的Model的属性添加BindingResult，
			// 当然BindingResult也是添加到Model中，只不过属性名上会加上一个固定前缀，
			// 这样子，如果后面某个属性想获取相应的BindingResult，则只需要利用（BindingResult.MODEL_KEY_PREFIX+属性名）就可以从Model中获取了。
			if (value != null && isBindingCandidate(name, value)) {
				String bindingResultKey = BindingResult.MODEL_KEY_PREFIX + name;
				if (!model.containsAttribute(bindingResultKey)) {
					WebDataBinder dataBinder = this.dataBinderFactory.createBinder(request, value, name);
					model.put(bindingResultKey, dataBinder.getBindingResult());
				}
			}
		}
	}

	/**
	 * Whether the given attribute requires a {@link BindingResult} in the model.
	 */
	private boolean isBindingCandidate(String attributeName, Object value) {
		// 说明其规则：
		// 规则1：当前属性值不是BindingResult、空值、数组、Collection、Map和简单类型的话都会返回true；
		// 规则2：当前属性只要在SessionAttributes中设置了，则就一定会返回true；
		// 规则3：不满足上述规则1和规则2，则返回false。
		if (attributeName.startsWith(BindingResult.MODEL_KEY_PREFIX)) {
			return false;
		}

		if (this.sessionAttributesHandler.isHandlerSessionAttribute(attributeName, value.getClass())) {
			return true;
		}

		return (!value.getClass().isArray() && !(value instanceof Collection) &&
				!(value instanceof Map) && !BeanUtils.isSimpleValueType(value.getClass()));
	}


	/**
	 * Derive the model attribute name for the given method parameter based on
	 * a {@code @ModelAttribute} parameter annotation (if present) or falling
	 * back on parameter type based conventions.
	 * @param parameter a descriptor for the method parameter
	 * @return the derived name
	 * @see Conventions#getVariableNameForParameter(MethodParameter)
	 */
	public static String getNameForParameter(MethodParameter parameter) {
		ModelAttribute ann = parameter.getParameterAnnotation(ModelAttribute.class);
		String name = (ann != null ? ann.value() : null);
		return (StringUtils.hasText(name) ? name : Conventions.getVariableNameForParameter(parameter));
	}

	/**
	 * Derive the model attribute name for the given return value. Results will be
	 * based on:
	 * <ol>
	 * <li>the method {@code ModelAttribute} annotation value
	 * <li>the declared return type if it is more specific than {@code Object}
	 * <li>the actual return value type
	 * </ol>
	 * @param returnValue the value returned from a method invocation
	 * @param returnType a descriptor for the return type of the method
	 * @return the derived name (never {@code null} or empty String)
	 */
	public static String getNameForReturnValue(@Nullable Object returnValue, MethodParameter returnType) {
		// 首先是先尝试获取该被@ModelAttribute所标注的方法上的这个注解中的value属性的值，
		// 如果这个值非空，则将其作为后面存入到Model中的属性的属性名返回。
		ModelAttribute ann = returnType.getMethodAnnotation(ModelAttribute.class);
		if (ann != null && StringUtils.hasText(ann.value())) {
			return ann.value();
		}
		// 否则，根据返回值的类型来生成属性名返回
		else {
			Method method = returnType.getMethod();
			Assert.state(method != null, "No handler method");
			Class<?> containingClass = returnType.getContainingClass();
			Class<?> resolvedType = GenericTypeResolver.resolveReturnType(method, containingClass);
			return Conventions.getVariableNameForReturnType(method, resolvedType, returnValue);
		}
	}


	private static class ModelMethod {

		private final InvocableHandlerMethod handlerMethod;

		private final Set<String> dependencies = new HashSet<>();

		public ModelMethod(InvocableHandlerMethod handlerMethod) {
			this.handlerMethod = handlerMethod;
			for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
				if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
					this.dependencies.add(getNameForParameter(parameter));
				}
			}
		}

		public InvocableHandlerMethod getHandlerMethod() {
			return this.handlerMethod;
		}

		public boolean checkDependencies(ModelAndViewContainer mavContainer) {
			for (String name : this.dependencies) {
				if (!mavContainer.containsAttribute(name)) {
					return false;
				}
			}
			return true;
		}

		public List<String> getUnresolvedDependencies(ModelAndViewContainer mavContainer) {
			List<String> result = new ArrayList<>(this.dependencies.size());
			for (String name : this.dependencies) {
				if (!mavContainer.containsAttribute(name)) {
					result.add(name);
				}
			}
			return result;
		}

		@Override
		public String toString() {
			return this.handlerMethod.getMethod().toGenericString();
		}
	}

}
