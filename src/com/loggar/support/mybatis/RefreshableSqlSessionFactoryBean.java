package com.loggar.support.mybatis;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.io.Resource;

/**
 * refresh xml mappers without restarting web-applicaton-server, local test.
 * 
 * <pre>
 * <beans:bean id="sqlSessionFactory" class="com.koi.support.mybatis.RefreshableSqlSessionFactoryBean">
		<beans:property name="dataSource" ref="dataSource" />
		<beans:property name="configLocation" value="classpath:spring/config/mybatis-config.xml" />
		<beans:property name="mapperLocations">
			<beans:array>
				<beans:value>classpath:com/poizn/mapper/*.xml</beans:value>
			</beans:array>
		</beans:property>
		<beans:property name="interval" value="5000" />
	</beans:bean>
 * </pre>
 */
public class RefreshableSqlSessionFactoryBean extends SqlSessionFactoryBean implements DisposableBean {
	private static final Logger logger = LoggerFactory.getLogger(RefreshableSqlSessionFactoryBean.class);

	private SqlSessionFactory proxy;
	private int interval = 500;

	private Timer timer;
	private TimerTask task;

	private Resource[] mapperLocations;

	/**
	 * 파일 감시 쓰레드가 실행중인지 여부.
	 */
	private boolean running = false;

	private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
	private final Lock r = rwl.readLock();
	private final Lock w = rwl.writeLock();

	public void setMapperLocations(Resource[] mapperLocations) {
		super.setMapperLocations(mapperLocations);
		this.mapperLocations = mapperLocations;
	}

	public void setInterval(int interval) {
		this.interval = interval;
	}

	/**
	 * @throws Exception
	 */
	public void refresh() throws Exception {
		if (logger.isInfoEnabled()) {
			logger.info("refreshing sqlMapClient.");
		}
		w.lock();
		try {
			super.afterPropertiesSet();

		} finally {
			w.unlock();
		}
	}

	/**
	 * 싱글톤 멤버로 SqlMapClient 원본 대신 프록시로 설정하도록 Override.
	 */
	public void afterPropertiesSet() throws Exception {
		super.afterPropertiesSet();

		setRefreshable();
	}

	private void setRefreshable() {
		proxy = (SqlSessionFactory) Proxy.newProxyInstance(SqlSessionFactory.class.getClassLoader(),
				new Class[] { SqlSessionFactory.class }, new InvocationHandler() {
					public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
						// logger.debug("method.getName() : " + method.getName());
						return method.invoke(getParentObject(), args);
					}
				});

		task = new TimerTask() {
			private Map<Resource, Long> map = new HashMap<Resource, Long>();

			public void run() {
				if (isModified()) {
					try {
						refresh();
					} catch (Exception e) {
						logger.error("caught exception", e);
					}
				}
			}

			private boolean isModified() {
				boolean retVal = false;

				if (mapperLocations != null) {
					for (int i = 0; i < mapperLocations.length; i++) {
						Resource mappingLocation = mapperLocations[i];
						retVal |= findModifiedResource(mappingLocation);
					}
				}

				return retVal;
			}

			private boolean findModifiedResource(Resource resource) {
				boolean retVal = false;
				List<String> modifiedResources = new ArrayList<String>();

				try {
					long modified = resource.lastModified();

					if (map.containsKey(resource)) {
						long lastModified = ((Long) map.get(resource)).longValue();

						if (lastModified != modified) {
							map.put(resource, new Long(modified));
							modifiedResources.add(resource.getDescription());
							retVal = true;
						}
					} else {
						map.put(resource, new Long(modified));
					}
				} catch (IOException e) {
					logger.error("caught exception", e);
				}
				if (retVal) {
					if (logger.isInfoEnabled()) {
						logger.info("modified files : " + modifiedResources);
					}
				}
				return retVal;
			}
		};

		timer = new Timer(true);
		resetInterval();

	}

	private Object getParentObject() throws Exception {
		r.lock();
		try {
			return super.getObject();

		} finally {
			r.unlock();
		}
	}

	public SqlSessionFactory getObject() {
		return this.proxy;
	}

	public Class<? extends SqlSessionFactory> getObjectType() {
		return (this.proxy != null ? this.proxy.getClass() : SqlSessionFactory.class);
	}

	public boolean isSingleton() {
		return true;
	}

	public void setCheckInterval(int ms) {
		interval = ms;

		if (timer != null) {
			resetInterval();
		}
	}

	private void resetInterval() {
		if (running) {
			timer.cancel();
			running = false;
		}
		if (interval > 0) {
			timer.schedule(task, 0, interval);
			running = true;
		}
	}

	@Override
	public void destroy() throws Exception {
		timer.cancel();
	}

}
