package com.github.lshtom.enterpriseApplication.mybatis;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.Reader;

/**
 * 工具类，主要用于获取SqlSessionFactory对象实例
 */
public class MybatisUtils {
	private static final SqlSessionFactory sqlSessionFactory;

	static {
		String resource = "com/github/lshtom/enterpriseApplication/mybatis/mybatis-config.xml";
		Reader reader = null;
		try {
			// 该Resources类是MyBatis提供的
			reader = Resources.getResourceAsReader(resource);
		} catch (IOException e) {
			e.printStackTrace();
		}
		sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
	}

	public static SqlSessionFactory getSqlSessionFactory() {
		return sqlSessionFactory;
	}
}
