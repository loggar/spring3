package com.loggar.util.generic;

import java.lang.invoke.MethodHandles;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggarReflectionUtilTest {
	private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	
	interface InterfaceA {
		String method1();
	}
	
	class ClassB implements InterfaceA {
		String fieldName1 = "nameOfClassB";
		
		@Override
		public String method1() {
			return this.getClass().getName() + "method1()";
			
		}
	}
	
	@Test
	public void test1() {
		ClassB testClass = new ClassB();
		
		logger.debug(testClass.getClass().toString());
		
		String testFieldName = "fieldName1";
		Class<?> testFieldClass = null;
		
		try {
			testFieldClass = LoggarReflectionUtil.getGenericType(testClass.getClass().getDeclaredField(testFieldName));
		} catch (NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
		}
		
		logger.debug(testFieldName + ":" + testFieldClass.toString());
	}
	
	@Test
	public void test2() {
		
	}
}
