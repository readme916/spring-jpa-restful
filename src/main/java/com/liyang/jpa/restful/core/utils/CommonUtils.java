package com.liyang.jpa.restful.core.utils;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liyang.jpa.restful.core.exception.NotFound404Exception;
import com.liyang.jpa.smart.query.db.SmartQuery;
import com.liyang.jpa.smart.query.db.structure.ColumnStucture;
import com.liyang.jpa.smart.query.db.structure.EntityStructure;

public class CommonUtils {

	public static String subResourceName(String resource, String subResource) {
		EntityStructure structure = SmartQuery.getStructure(resource);
		ColumnStucture columnStucture = structure.getObjectFields().get(subResource);
		if(columnStucture==null) {
			throw new NotFound404Exception(resource+":"+subResource);
		}
		Class<?> targetEntity = columnStucture.getTargetEntity();
		EntityStructure subStructure = SmartQuery.getStructure(targetEntity);
		return subStructure.getName();
	}

	public static String objectToString(Object object) throws JsonProcessingException {

		ObjectMapper objectMapper = new ObjectMapper();
		String writeValueAsString = null;

		writeValueAsString = objectMapper.writeValueAsString(object);

		return writeValueAsString;
	}

	public static <T> T stringToObject(String jsonString, Class<T> clazz)
			throws JsonParseException, JsonMappingException, IOException {

		ObjectMapper objectMapper = new ObjectMapper();

		return objectMapper.readValue(jsonString, clazz);

	}

	public static <T> T mapToObject(Map<String, Object> map, Class<T> beanClass) throws IOException {
		if (map == null) {
			return null;
		}

		ObjectMapper objectMapper = new ObjectMapper();
		T obj = objectMapper.convertValue(map, beanClass);

		return obj;
	}

	public static Map<String, Object> objectToMap(Object obj) {
		if (obj == null) {
			return null;
		}

		ObjectMapper objectMapper = new ObjectMapper();
		Map<String, Object> mappedObject = objectMapper.convertValue(obj, Map.class);

		return mappedObject;
	}

	public static Map<String, Object> stringToMap(String str)
			throws JsonParseException, JsonMappingException, IOException {
		if (str == null) {
			return null;
		}

		ObjectMapper objectMapper = new ObjectMapper();
		Map<String, Object> mappedObject = null;

		mappedObject = objectMapper.readValue(str, Map.class);

		return mappedObject;
	}

	public static HashMap<String, String> validate(Object object) {

		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		Validator validator = factory.getValidator();
		Set<ConstraintViolation<Object>> validates = validator.validate(object);

		HashMap<String, String> errors = new HashMap<String, String>();
		if (!validates.isEmpty()) {
			for (ConstraintViolation<Object> constraint : validates) {
				ValidateError validateError = new ValidateError();
				validateError.setMessage(constraint.getMessage());
				validateError.setProperty(constraint.getPropertyPath().toString());
				errors.put(constraint.getPropertyPath().toString(), constraint.getMessage());
			}
		}
		return errors;
	}

	public static class ValidateError {

		public String message;
		public String property;

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		public String getProperty() {
			return property;
		}

		public void setProperty(String property) {
			this.property = property;
		}

	}

	public static String[] getNullPropertyNames(Object source) {
		final BeanWrapper src = new BeanWrapperImpl(source);
		java.beans.PropertyDescriptor[] pds = src.getPropertyDescriptors();

		Set<String> emptyNames = new HashSet<String>();
		for (java.beans.PropertyDescriptor pd : pds) {
			Object srcValue = src.getPropertyValue(pd.getName());
			if (srcValue == null)
				emptyNames.add(pd.getName());
		}
		String[] result = new String[emptyNames.size()];
		return emptyNames.toArray(result);
	}

	public static Set<String> getNotNullPropertyNames(Object source) {
		final BeanWrapper src = new BeanWrapperImpl(source);
		java.beans.PropertyDescriptor[] pds = src.getPropertyDescriptors();
		Set<String> notEmptyNames = new HashSet<String>();
		for (java.beans.PropertyDescriptor pd : pds) {
			Object srcValue = src.getPropertyValue(pd.getName());
			if (!pd.getName().equals("class") && srcValue != null)
				notEmptyNames.add(pd.getName());
		}
		return notEmptyNames;
	}

	public static void copyPropertiesIgnoreNull(Object src, Object target) {
		BeanUtils.copyProperties(src, target, getNullPropertyNames(src));

	}

	public static HashMap<String, Object> copyEntitySimpleProperitesToMap(EntityStructure structure, Object src) {
		BeanWrapper srcWrapper = new BeanWrapperImpl(src);
		if (srcWrapper.getPropertyValue("uuid") == null) {
			return new HashMap<String, Object>();
		}
		HashMap<String, Object> hashMap = new HashMap<String, Object>();
		Optional findById = structure.getJpaRepository().findById(srcWrapper.getPropertyValue("uuid").toString());
		if (findById.isPresent()) {
			Object entity = findById.get();
			BeanWrapperImpl entityWrapperImpl = new BeanWrapperImpl(entity);
			Set<String> keySet = structure.getSimpleFields().keySet();
			for (String key : keySet) {
				if (entityWrapperImpl.getPropertyValue(key) != null) {
					hashMap.put(key, entityWrapperImpl.getPropertyValue(key));
				}
			}
		}
		return hashMap;
	}

	public static boolean isPackageClassObject(Object obj) {
		try {
			return ((Class<?>) obj.getClass().getField("TYPE").get(null)).isPrimitive();
		} catch (Exception e) {
			return false;
		}
	}

	public static boolean isPackageClass(Class<?> cls) {
		try {
			return ((Class<?>) cls.getField("TYPE").get(null)).isPrimitive();
		} catch (Exception e) {
			return false;
		}
	}

}
