package com.liyang.jpa.restful.core.service;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liyang.jpa.mysql.config.JpaSmartQuerySupport;
import com.liyang.jpa.mysql.db.SmartQuery;
import com.liyang.jpa.mysql.db.structure.ColumnJoinType;
import com.liyang.jpa.mysql.db.structure.ColumnStucture;
import com.liyang.jpa.mysql.db.structure.EntityStructure;
import com.liyang.jpa.restful.core.exception.BusinessException;
import com.liyang.jpa.restful.core.exception.PostFormatException;
import com.liyang.jpa.restful.core.interceptor.JpaRestfulDeleteInterceptor;
import com.liyang.jpa.restful.core.interceptor.JpaRestfulPostInterceptor;
import com.liyang.jpa.restful.core.response.HTTPPostOkResponse;
import com.liyang.jpa.restful.core.utils.CommonUtils;
import com.liyang.jpa.restful.core.utils.InterceptorComparator;

@Service
public class DeleteService extends BaseService {
	private Map<String, JpaRestfulDeleteInterceptor> interceptors;
	protected final static Logger logger = LoggerFactory.getLogger(DeleteService.class);

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.interceptors = applicationContext.getBeansOfType(JpaRestfulDeleteInterceptor.class);

	}

	@Transactional(readOnly = false)
	public Object delete(String resource, String resourceId) {
		checkResource(resource, null);
		HashMap<Object, Object> context = new HashMap<Object, Object>();
		EntityStructure structure = JpaSmartQuerySupport.getStructure(resource);
		Object oldInstance;
		Optional oldInstanceOptional = structure.getJpaRepository().findById(resourceId);
		if (!oldInstanceOptional.isPresent()) {
			throw new PostFormatException(3100, "数据不存在", "");
		} else {
			oldInstance = oldInstanceOptional.get();
		}
		String requestPath = "/" + resource + "/" + resourceId;
		applyPreInterceptor(requestPath, oldInstance, context);

		recursiveDelete(structure, oldInstance);

		HTTPPostOkResponse httpPostOkResponse = new HTTPPostOkResponse();
		httpPostOkResponse.setUuid(resourceId);
		return applyPostInterceptor(requestPath, httpPostOkResponse, context);
	}

	@Transactional(readOnly = false)
	public Object delete(String resource, String resourceId, String subResource, String subResourceId) {
		checkResource(resource, null);
		HashMap<Object, Object> context = new HashMap<Object, Object>();
		EntityStructure structure = JpaSmartQuerySupport.getStructure(resource);
		long fetchCount = SmartQuery.fetchCount(resource,
				"uuid=" + resourceId + "&" + subResource + ".uuid=" + subResourceId);
		if (fetchCount == 0) {
			throw new PostFormatException(3330, "数据不存在", "");
		} else {
			Object owner;
			Optional ownerOptional = structure.getJpaRepository().findById(resourceId);
			owner = ownerOptional.get();
			EntityStructure subResourceStructure = JpaSmartQuerySupport
					.getStructure(CommonUtils.subResourceName(resource, subResource));
			Optional oldInstanceOptional = subResourceStructure.getJpaRepository().findById(subResourceId);
			Object subResourceObject = oldInstanceOptional.get();
			String requestPath = "/" + resource + "/" + resourceId + "/" + subResource + "/" + subResourceId;
			applyPreInterceptor(requestPath, subResourceObject, context);

			subDelete(structure, owner, subResource, subResourceStructure, subResourceObject);

			HTTPPostOkResponse httpPostOkResponse = new HTTPPostOkResponse();
			httpPostOkResponse.setUuid(subResourceId);
			return applyPostInterceptor(requestPath, httpPostOkResponse, context);
		}
	}

	private void subDelete(EntityStructure structure, Object owner, String subResource,
			EntityStructure subResourceStructure, Object subResourceObject) {
		ColumnStucture columnStucture = structure.getObjectFields().get(subResource);
		ColumnJoinType joinType = columnStucture.getJoinType();
		if (joinType.equals(ColumnJoinType.ONE_TO_ONE)) {
			if (columnStucture.getMappedBy() != null) {
				BeanWrapperImpl targetWrapper = new BeanWrapperImpl(subResourceObject);
				targetWrapper.setPropertyValue(columnStucture.getMappedBy(), null);
				subResourceStructure.getJpaRepository().save(subResourceObject);

			} else {
				BeanWrapperImpl ownerWrapper = new BeanWrapperImpl(owner);
				ownerWrapper.setPropertyValue(subResource, null);
				structure.getJpaRepository().save(owner);
			}
		} else if (joinType.equals(ColumnJoinType.MANY_TO_ONE)) {
			BeanWrapperImpl ownerWrapper = new BeanWrapperImpl(owner);
			ownerWrapper.setPropertyValue(subResource, null);
			structure.getJpaRepository().save(owner);
		} else if (joinType.equals(ColumnJoinType.ONE_TO_MANY)) {
			BeanWrapperImpl targetWrapper = new BeanWrapperImpl(subResourceObject);
			targetWrapper.setPropertyValue(columnStucture.getMappedBy(), null);
			subResourceStructure.getJpaRepository().save(subResourceObject);
		} else if (joinType.equals(ColumnJoinType.MANY_TO_MANY)) {
			if (columnStucture.getMappedBy() != null) {
				BeanWrapperImpl wrapper = new BeanWrapperImpl(subResourceObject);
				Object targetObject = wrapper.getPropertyValue(columnStucture.getMappedBy());
				Collection c = (Collection) targetObject;
				c.remove(owner);
				subResourceStructure.getJpaRepository().save(subResourceObject);
			} else {
				BeanWrapperImpl wrapper = new BeanWrapperImpl(owner);
				Object targetObject = wrapper.getPropertyValue(subResource);
				Collection c = (Collection) targetObject;
				c.remove(subResourceObject);
				structure.getJpaRepository().save(owner);
			}
		}

	}

	private void recursiveDelete(EntityStructure structure, Object oldInstance) {
		BeanWrapperImpl ownerWrapper = new BeanWrapperImpl(oldInstance);
		Set<Entry<String, ColumnStucture>> entrySet = structure.getObjectFields().entrySet();
		for (Entry<String, ColumnStucture> entry : entrySet) {
			deleteForeignKey(structure, oldInstance, entry.getKey());
		}
		structure.getJpaRepository().delete(oldInstance);
	}

	private void deleteForeignKey(EntityStructure structure, Object oldInstance, String key) {
		ColumnStucture columnStucture = structure.getObjectFields().get(key);
		ColumnJoinType joinType = columnStucture.getJoinType();
		Class<?> targetEntity = columnStucture.getTargetEntity();
		EntityStructure targetStructure = JpaSmartQuerySupport.getStructure(targetEntity);
		if (joinType.equals(ColumnJoinType.ONE_TO_ONE)) {
			if (columnStucture.getMappedBy() != null) {
				BeanWrapperImpl wrapper = new BeanWrapperImpl(oldInstance);
				Object targetObject = wrapper.getPropertyValue(key);
				if (targetObject != null) {
					BeanWrapperImpl targetWrapper = new BeanWrapperImpl(targetObject);
					targetWrapper.setPropertyValue(columnStucture.getMappedBy(), null);
					targetStructure.getJpaRepository().save(targetObject);
				}
			}
		} else if (joinType.equals(ColumnJoinType.ONE_TO_MANY)) {

			BeanWrapperImpl wrapper = new BeanWrapperImpl(oldInstance);
			Object targetObject = wrapper.getPropertyValue(key);
			if (targetObject != null) {
				Collection c = (Collection) targetObject;
				for (Object object : c) {
					BeanWrapperImpl targetWrapper = new BeanWrapperImpl(object);
					targetWrapper.setPropertyValue(columnStucture.getMappedBy(), null);
					targetStructure.getJpaRepository().save(object);
				}
			}
		} else if (joinType.equals(ColumnJoinType.MANY_TO_MANY)) {

			if (columnStucture.getMappedBy() != null) {
				BeanWrapperImpl wrapper = new BeanWrapperImpl(oldInstance);
				Object targetObject = wrapper.getPropertyValue(key);
				if (targetObject != null) {
					Collection c = (Collection) targetObject;
					for (Object object : c) {
						BeanWrapperImpl targetWrapper = new BeanWrapperImpl(object);
						Collection c2 = (Collection) targetWrapper.getPropertyValue(columnStucture.getMappedBy());
						c2.remove(oldInstance);
						targetStructure.getJpaRepository().save(object);
					}
				}
			} else {
				BeanWrapperImpl wrapper = new BeanWrapperImpl(oldInstance);
				wrapper.setPropertyValue(key, null);
				structure.getJpaRepository().save(oldInstance);
			}
		}
	}

	private boolean applyPreInterceptor(String requestPath, Object oldInstance, Map<Object, Object> context) {
		if (this.interceptors != null && this.interceptors.size() != 0) {

			PathMatcher matcher = new AntPathMatcher();

			Collection<JpaRestfulDeleteInterceptor> values = this.interceptors.values();
			JpaRestfulDeleteInterceptor[] interceptors = values.toArray(new JpaRestfulDeleteInterceptor[values.size()]);
			Arrays.sort(interceptors, new InterceptorComparator());
			// 顺序执行拦截器的preHandle方法，如果返回false,则调用triggerAfterCompletion方法
			for (int i = 0; i < interceptors.length; i++) {
				JpaRestfulDeleteInterceptor interceptor = interceptors[i];
				String[] patternPath = interceptor.path();
				boolean matched = false;
				for (String pattern : patternPath) {
					if (matcher.match(pattern, requestPath)) {
						matched = true;
					}
				}
				if (matched && !interceptor.preHandle(requestPath, oldInstance, context)) {
					throw new BusinessException(2000, "数据被拦截", "路径：" + interceptor.path());
				}
			}
		}
		return true;
	}

	private HTTPPostOkResponse applyPostInterceptor(String requestPath, HTTPPostOkResponse httpPostOkResponse,
			Map<Object, Object> context) {
		if (this.interceptors != null && this.interceptors.size() != 0) {

			PathMatcher matcher = new AntPathMatcher();

			Collection<JpaRestfulDeleteInterceptor> values = this.interceptors.values();
			JpaRestfulDeleteInterceptor[] interceptors = values.toArray(new JpaRestfulDeleteInterceptor[values.size()]);
			Arrays.sort(interceptors, new InterceptorComparator());
			for (int i = interceptors.length - 1; i >= 0; i--) {
				JpaRestfulDeleteInterceptor interceptor = interceptors[i];
				String[] patternPath = interceptor.path();
				boolean matched = false;
				for (String pattern : patternPath) {
					if (matcher.match(pattern, requestPath)) {
						matched = true;
					}
				}
				if (matched) {
					httpPostOkResponse = interceptor.postHandle(requestPath, httpPostOkResponse, context);
				}
			}
		}
		return httpPostOkResponse;

	}
}
