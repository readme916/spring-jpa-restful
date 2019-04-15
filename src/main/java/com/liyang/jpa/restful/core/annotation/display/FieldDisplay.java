package com.liyang.jpa.restful.core.annotation.display;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(ElementType.FIELD)
@Inherited
public @interface FieldDisplay {
	String label();
	String prop() default "";
	int width() default 0;
	Position[] position() default Position.DETAIL;
	int order() default 0;
	boolean file() default false;

}
