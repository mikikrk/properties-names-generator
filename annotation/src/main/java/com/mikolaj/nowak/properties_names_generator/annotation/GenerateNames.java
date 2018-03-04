package com.mikolaj.nowak.properties_names_generator.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface GenerateNames {

    Class<? extends Annotation>[] nameAnnotations() default {};
}

