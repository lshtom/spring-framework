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

package org.springframework.beans.factory.xml;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Default implementation of the {@link BeanDefinitionDocumentReader} interface that
 * reads bean definitions according to the "spring-beans" DTD and XSD format
 * (Spring's default XML bean definition format).
 *
 * <p>The structure, elements, and attribute names of the required XML document
 * are hard-coded in this class. (Of course a transform could be run if necessary
 * to produce this format). {@code <beans>} does not need to be the root
 * element of the XML document: this class will parse all bean definition elements
 * in the XML file, regardless of the actual root element.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Erik Wiersma
 * @since 18.12.2003
 */
public class DefaultBeanDefinitionDocumentReader implements BeanDefinitionDocumentReader {

	public static final String BEAN_ELEMENT = BeanDefinitionParserDelegate.BEAN_ELEMENT;

	public static final String NESTED_BEANS_ELEMENT = "beans";

	public static final String ALIAS_ELEMENT = "alias";

	public static final String NAME_ATTRIBUTE = "name";

	public static final String ALIAS_ATTRIBUTE = "alias";

	public static final String IMPORT_ELEMENT = "import";

	public static final String RESOURCE_ATTRIBUTE = "resource";

	public static final String PROFILE_ATTRIBUTE = "profile";


	protected final Log logger = LogFactory.getLog(getClass());

	@Nullable
	private XmlReaderContext readerContext;

	@Nullable
	private BeanDefinitionParserDelegate delegate;


	/**
	 * This implementation parses bean definitions according to the "spring-beans" XSD
	 * (or DTD, historically).
	 * <p>Opens a DOM Document; then initializes the default settings
	 * specified at the {@code <beans/>} level; then parses the contained bean definitions.
	 */
	@Override
	public void registerBeanDefinitions(Document doc, XmlReaderContext readerContext) {
		// XmlReaderContext对象实例内部又持有资源Resource、XmlBeanDefinitonReader实例（其实内部还持有容器的对象实例）等，
		// 所以readerContext就如其名字一样，就是也给读取器上下文，这个上下文还持有容器的实例，
		// 所以通过将Bean注册到这个上下文中就完成了将Bean注册到容器中
		this.readerContext = readerContext;
		doRegisterBeanDefinitions(doc.getDocumentElement());
	}

	/**
	 * Return the descriptor for the XML resource that this parser works on.
	 */
	protected final XmlReaderContext getReaderContext() {
		Assert.state(this.readerContext != null, "No XmlReaderContext available");
		return this.readerContext;
	}

	/**
	 * Invoke the {@link org.springframework.beans.factory.parsing.SourceExtractor}
	 * to pull the source metadata from the supplied {@link Element}.
	 */
	@Nullable
	protected Object extractSource(Element ele) {
		return getReaderContext().extractSource(ele);
	}


	/**
	 * Register each bean definition within the given root {@code <beans/>} element.
	 */
	@SuppressWarnings("deprecation")  // for Environment.acceptsProfiles(String...)
	protected void doRegisterBeanDefinitions(Element root) {
		// Any nested <beans> elements will cause recursion in this method. In
		// order to propagate and preserve <beans> default-* attributes correctly,
		// keep track of the current (parent) delegate, which may be null. Create
		// the new (child) delegate with a reference to the parent for fallback purposes,
		// then ultimately reset this.delegate back to its original (parent) reference.
		// this behavior emulates a stack of delegates without actually necessitating one.
		// 注：this.delegate默认其实为空（是首次为空）
		// 如果<beans/>标签中又含有<beans/>标签，那么会再次递归执行到此方法，此时的delegate就不再为空了
		BeanDefinitionParserDelegate parent = this.delegate;
		// 创建委托（BeanDefinitionParserDelegate类型），即将Bean的解析委托到这个类中进行,
		// 同样要注意到的是这个委托对象内部是持有readerContext实例的，
		// 也就意味这其持有XmlBeanDefinitionRader对象实例，进一步的意味着持有BeanFactory实例
		this.delegate = createDelegate(getReaderContext(), root, parent);

		// 1、isDefaultNamespace方法判断是否为默认命名空间（Spring2.0以后的XML配置文件默认采用Schema格式）
		// Spring的XML配置文件的默认命名空间为<beans></beans>标签的属性xmlns的默认值，为http://www.springframework.org/schema/beans
		// 所以isDefaultNamespace方法的作用就是判断当前读取的配置文件中的<beans></beans>标签的属性xmlns的值
		// 2、root其实指向的是XML配置文件的最外层的<beans></beans>标签，也就是根标签
		if (this.delegate.isDefaultNamespace(root)) {
			// 获取<beans></beans>标签中的profile属性的值，
			// 该profile属性可以有多个值，可用于指定<beans></beans>标签内的Bean在什么样的环境下被注册（可通过在命令行中使用spring.profiles.active来激活某个/些场景）
			String profileSpec = root.getAttribute(PROFILE_ATTRIBUTE);
			if (StringUtils.hasText(profileSpec)) {
				// 对profile属性的值进行分割，得到一个个具体的profile值
				String[] specifiedProfiles = StringUtils.tokenizeToStringArray(
						profileSpec, BeanDefinitionParserDelegate.MULTI_VALUE_ATTRIBUTE_DELIMITERS);
				// We cannot use Profiles.of(...) since profile expressions are not supported
				// in XML config. See SPR-12458 for details.
				// 关键在于acceptsProfiles方法，该方法的作用是：判断<beans></beans>标签中的profile是否生效，
				// 若没有生效，则直接返回，不再对当前XML配置文件中的Bean进行解析注册
				// acceptsProfiles中的逻辑不复杂，关键点其实就是:取Environment中的spring.profiles.active的值与specifiedProfiles的值做比较，
				// 若spring.profiles.active的值包含有specifiedProfiles的值，则生效
				if (!getReaderContext().getEnvironment().acceptsProfiles(specifiedProfiles)) {
					if (logger.isDebugEnabled()) {
						logger.debug("Skipped XML bean definition file due to specified profiles [" + profileSpec +
								"] not matching: " + getReaderContext().getResource());
					}
					return;
				}
			}
		}

		// 下面这三行代码相当于是定义了算法（逻辑）的骨架，而preProcessXml和postProcessXml是可被覆盖的【模板方法模式】
		// preProcessXml方法和postProcessXml方法是空方法，是留给用于定义的子类中进行重写，相当于是提供了个扩展点
		preProcessXml(root);
		// Bean的解析注册入口，注意Bean的解析是通过委托类对象来进行的，职责分开
		parseBeanDefinitions(root, this.delegate);
		postProcessXml(root);

		this.delegate = parent;
	}

	protected BeanDefinitionParserDelegate createDelegate(
			XmlReaderContext readerContext, Element root, @Nullable BeanDefinitionParserDelegate parentDelegate) {

		BeanDefinitionParserDelegate delegate = new BeanDefinitionParserDelegate(readerContext);
		delegate.initDefaults(root, parentDelegate);
		return delegate;
	}

	/**
	 * Parse the elements at the root level in the document:
	 * "import", "alias", "bean".
	 * @param root the DOM root element of the document
	 */
	protected void parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate) {
		// XML配置文件中有两大类声明：一类是默认，即<bean>，另一类是自定义的，如<tx:annotation-driven>
		// <bean>标签的namespaceURI的值仍然为http://www.springframework.org/schema/beans
		// 或者说更准确的说法是在默认命名空间http://www.springframework.org/schema/beans所指定的XSD文件spring-beans.xsd中包含了对<bean>的定义，
		// 故当逐层级遍历到<bean>标签、<import>标签等，获取其namespaceURI的值均为http://www.springframework.org/schema/beans
		// 判断根节点是否为默认命名空间，若是则进行解析
		if (delegate.isDefaultNamespace(root)) {
			// 获取根节点的所有各子节点，并逐个遍历解析处理
			NodeList nl = root.getChildNodes();
			for (int i = 0; i < nl.getLength(); i++) {
				Node node = nl.item(i);
				if (node instanceof Element) {
					Element ele = (Element) node;
					if (delegate.isDefaultNamespace(ele)) {
						parseDefaultElement(ele, delegate);
					}
					else {
						// 根节点为<beans></beans>属于默认命名空间，
						// 但其子节点可能会是诸如<tx:annotation-driven>这种自定义标签
						delegate.parseCustomElement(ele);
					}
				}
			}
		}
		else {
			// 最外层的根节点都不属于默认命名空间，也就说明其各子节点不会含有默认命名空间中的<bean>、<import>等这些
			delegate.parseCustomElement(root);
		}
	}

	private void parseDefaultElement(Element ele, BeanDefinitionParserDelegate delegate) {
		// 对于默认命名空间里的元素有以下几种(<import>、<alias>、<bean>、<beans>)
		// 对于每一种都要分类进行处理
		if (delegate.nodeNameEquals(ele, IMPORT_ELEMENT)) {
			importBeanDefinitionResource(ele);
		}
		else if (delegate.nodeNameEquals(ele, ALIAS_ELEMENT)) {
			// 注意：该方法中对别名的处理与processBeanDefinition方法中对别名的处理有点不同，
			// 写在<bean>标签的name属性中的别名会被分割，然后分别进行注册。
			// 而写在<alias>标签中的alias属性中的别名不会被分割，直接注册
			processAliasRegistration(ele);
		}
		else if (delegate.nodeNameEquals(ele, BEAN_ELEMENT)) {
			// 这个delegate对象是负责<bean>标签解析的，所以也只有在此处传入了该对象，其他三处都没有用
			// processBeanDefinition方法内部就四大步骤：
			// 1、<bean>标签中的默认属性、默认子标签的解析（也就是指默认命名空间下的属性和子标签）
			// 2、<bean>标签中非默认属性、非默认子标签的解析（也就是在上一步得到的BeanDefinitionHolder对象的基础上进行装饰）
			// 3、进行Bean的注册，包括将Bean的定义信息注册到Bean定义注册表中和将别名注册到别名注册表中
			// 4、发布该Bean已经注册的事件
			processBeanDefinition(ele, delegate);
		}
		else if (delegate.nodeNameEquals(ele, NESTED_BEANS_ELEMENT)) {
			// recurse
			doRegisterBeanDefinitions(ele);
		}
	}

	/**
	 * Parse an "import" element and load the bean definitions
	 * from the given resource into the bean factory.
	 */
	protected void importBeanDefinitionResource(Element ele) {
		// 获取配置文件的路径
		String location = ele.getAttribute(RESOURCE_ATTRIBUTE);
		if (!StringUtils.hasText(location)) {
			getReaderContext().error("Resource location must not be empty", ele);
			return;
		}

		// Resolve system properties: e.g. "${user.dir}"
		// 解析配置文件的路径中的占位符
		location = getReaderContext().getEnvironment().resolveRequiredPlaceholders(location);

		Set<Resource> actualResources = new LinkedHashSet<>(4);

		// Discover whether the location is an absolute or relative URI
		// 在得到location后（已经处理了占位符），需进一步判断为绝对路径还是相对路径：
		// 若是绝对路径，则直接递归调用bean的解析过程；
		// 若是相对路径，则要先计算出绝对路径再进行解析
		// Tips：Spring中的递归调用往往不局限于方法内，而更多的时类之间的调用，可能是跨越了多个类的，如A类调B类、B类调C类，C类又回到A类等
		boolean absoluteLocation = false;
		try {
			absoluteLocation = ResourcePatternUtils.isUrl(location) || ResourceUtils.toURI(location).isAbsolute();
		}
		catch (URISyntaxException ex) {
			// cannot convert to an URI, considering the location relative
			// unless it is the well-known Spring prefix "classpath*:"
		}

		// Absolute or relative?
		if (absoluteLocation) {
			try {
				int importCount = getReaderContext().getReader().loadBeanDefinitions(location, actualResources);
				if (logger.isTraceEnabled()) {
					logger.trace("Imported " + importCount + " bean definitions from URL location [" + location + "]");
				}
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error(
						"Failed to import bean definitions from URL location [" + location + "]", ele, ex);
			}
		}
		else {
			// No URL -> considering resource location as relative to the current file.
			try {
				int importCount;
				Resource relativeResource = getReaderContext().getResource().createRelative(location);
				if (relativeResource.exists()) {
					importCount = getReaderContext().getReader().loadBeanDefinitions(relativeResource);
					actualResources.add(relativeResource);
				}
				else {
					String baseLocation = getReaderContext().getResource().getURL().toString();
					importCount = getReaderContext().getReader().loadBeanDefinitions(
							StringUtils.applyRelativePath(baseLocation, location), actualResources);
				}
				if (logger.isTraceEnabled()) {
					logger.trace("Imported " + importCount + " bean definitions from relative location [" + location + "]");
				}
			}
			catch (IOException ex) {
				getReaderContext().error("Failed to resolve current resource location", ele, ex);
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error(
						"Failed to import bean definitions from relative location [" + location + "]", ele, ex);
			}
		}
		Resource[] actResArray = actualResources.toArray(new Resource[0]);
		getReaderContext().fireImportProcessed(location, actResArray, extractSource(ele));
	}

	/**
	 * Process the given alias element, registering the alias with the registry.
	 */
	protected void processAliasRegistration(Element ele) {
		String name = ele.getAttribute(NAME_ATTRIBUTE);
		String alias = ele.getAttribute(ALIAS_ATTRIBUTE);
		boolean valid = true;
		if (!StringUtils.hasText(name)) {
			getReaderContext().error("Name must not be empty", ele);
			valid = false;
		}
		if (!StringUtils.hasText(alias)) {
			getReaderContext().error("Alias must not be empty", ele);
			valid = false;
		}
		if (valid) {
			try {
				getReaderContext().getRegistry().registerAlias(name, alias);
			}
			catch (Exception ex) {
				getReaderContext().error("Failed to register alias '" + alias +
						"' for bean with name '" + name + "'", ele, ex);
			}
			getReaderContext().fireAliasRegistered(name, alias, extractSource(ele));
		}
	}

	/**
	 * Process the given bean element, parsing the bean definition
	 * and registering it with the registry.
	 */
	protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate) {
		// 步骤1：利用委托对象的parseBeanDefinitionElement方法来进行Bean元素解析,
		// 此时得到的bdHolder已经包含了<bean>标签下的各种属性及子标签了
		BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
		if (bdHolder != null) {
			// 步骤2：考虑到<bean>标签的子节点下可能还有自定义属性，如果存在则再进行解析
			bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);
			try {
				// Register the final decorated instance.
				// 步骤3：根据最终得到的bdHolder对象进行注册
				BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error("Failed to register bean definition with name '" +
						bdHolder.getBeanName() + "'", ele, ex);
			}
			// Send registration event.
			// 步骤4：发布事件，通知相关的监听器：这个Bean已经注册完成了
			getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
		}
	}


	/**
	 * Allow the XML to be extensible by processing any custom element types first,
	 * before we start to process the bean definitions. This method is a natural
	 * extension point for any other custom pre-processing of the XML.
	 * <p>The default implementation is empty. Subclasses can override this method to
	 * convert custom elements into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 * @see #getReaderContext()
	 */
	protected void preProcessXml(Element root) {
	}

	/**
	 * Allow the XML to be extensible by processing any custom element types last,
	 * after we finished processing the bean definitions. This method is a natural
	 * extension point for any other custom post-processing of the XML.
	 * <p>The default implementation is empty. Subclasses can override this method to
	 * convert custom elements into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 * @see #getReaderContext()
	 */
	protected void postProcessXml(Element root) {
	}

}
