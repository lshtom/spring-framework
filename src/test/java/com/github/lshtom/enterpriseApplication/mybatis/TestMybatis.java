package com.github.lshtom.enterpriseApplication.mybatis;

import com.github.lshtom.enterpriseApplication.mybatis.mapper.UserMapper;
import com.github.lshtom.enterpriseApplication.mybatis.model.User;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Spring整合Mybatis测试
 */
public class TestMybatis {

	@Test
	public void testGetUser() {
		ApplicationContext context = new ClassPathXmlApplicationContext("com/github/lshtom/enterpriseApplication/mybatis/mybatis-spring-config.xml");
		UserMapper userMapper = context.getBean("userMapper", UserMapper.class);
		User user = userMapper.getUser(3);
		System.out.println(user);
	}

	@Test
	public void testGetUser2() {
		ApplicationContext context = new ClassPathXmlApplicationContext("com/github/lshtom/enterpriseApplication/mybatis/mybatis-spring-config.xml");
		SqlSessionFactory sqlSessionFactory = context.getBean("sqlSessionFactory", SqlSessionFactory.class);
		SqlSession sqlSession = sqlSessionFactory.openSession();
		try {
			UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
			User user = userMapper.getUser(3);
			System.out.println(user);
		} finally {
			sqlSession.close();
		}
	}
}
