/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.web.servlet.handler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.lang.Nullable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;

/**
 * Abstract base class for
 * {@link org.springframework.web.servlet.HandlerExceptionResolver HandlerExceptionResolver}
 * implementations that support handling exceptions from handlers of type {@link HandlerMethod}.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public abstract class AbstractHandlerMethodExceptionResolver extends AbstractHandlerExceptionResolver {

	/**
	 * Checks if the handler is a {@link HandlerMethod} and then delegates to the
	 * base class implementation of {@code #shouldApplyTo(HttpServletRequest, Object)}
	 * passing the bean of the {@code HandlerMethod}. Otherwise returns {@code false}.
	 */
	@Override
	protected boolean shouldApplyTo(HttpServletRequest request, @Nullable Object handler) {
		// 说明：重写了父类AbstractHandlerExceptionResolver的shouldApplyTo方法，
		// 主要是针对方法级别的Handler进行处理，
		// 因为如果入参handler为HandlerMethod类型，那么父类所能处理的Handler其实应该是该method所在的Bean类实例,
		// 所以该方法的逻辑关键就是针对入参handler为HandlerMethod进行处理。

		if (handler == null) {
			return super.shouldApplyTo(request, null);
		}
		else if (handler instanceof HandlerMethod) {
			HandlerMethod handlerMethod = (HandlerMethod) handler;
			// 获取method所在的Bean类，这才是SpringMVC中所说的Handler的一般性指代。
			handler = handlerMethod.getBean();
			return super.shouldApplyTo(request, handler);
		}
		else {
			return false;
		}
	}

	@Override
	@Nullable
	protected final ModelAndView doResolveException(
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler, Exception ex) {

		return doResolveHandlerMethodException(request, response, (HandlerMethod) handler, ex);
	}

	/**
	 * Actually resolve the given exception that got thrown during on handler execution,
	 * returning a ModelAndView that represents a specific error page if appropriate.
	 * <p>May be overridden in subclasses, in order to apply specific exception checks.
	 * Note that this template method will be invoked <i>after</i> checking whether this
	 * resolved applies ("mappedHandlers" etc), so an implementation may simply proceed
	 * with its actual exception handling.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handlerMethod the executed handler method, or {@code null} if none chosen at the time
	 * of the exception (for example, if multipart resolution failed)
	 * @param ex the exception that got thrown during handler execution
	 * @return a corresponding ModelAndView to forward to, or {@code null} for default processing
	 */
	@Nullable
	protected abstract ModelAndView doResolveHandlerMethodException(
			HttpServletRequest request, HttpServletResponse response, @Nullable HandlerMethod handlerMethod, Exception ex);

}
