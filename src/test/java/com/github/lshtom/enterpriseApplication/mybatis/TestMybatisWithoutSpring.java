package com.github.lshtom.enterpriseApplication.mybatis;

import com.github.lshtom.enterpriseApplication.mybatis.mapper.UserMapper;
import com.github.lshtom.enterpriseApplication.mybatis.model.User;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Test;

/**
 * 测试不使用Spring，直接使用原生的Mybatis进行开发
 */
public class TestMybatisWithoutSpring {
	static SqlSessionFactory sqlSessionFactory = null;
	static {
		sqlSessionFactory = MybatisUtils.getSqlSessionFactory();
	}

	@Test
	public void testAdd() {
		SqlSession sqlSession = sqlSessionFactory.openSession();
		try {
			UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
			User user = new User();
			user.setName("黎明");
			user.setAge(41);
			user.setSex("男");
			userMapper.insertUser(user);
			sqlSession.commit();
		} finally {
			sqlSession.close();
		}
	}

	@Test
	public void testGetUser() {
		SqlSession sqlSession = sqlSessionFactory.openSession();
		try {
			UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
			User user = userMapper.getUser(2);
			System.out.println(user);
		} finally {
			sqlSession.close();
		}
	}
}
