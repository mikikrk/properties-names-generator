package com.mikolaj.nowak.properties_names_generator.processor;

import com.mikolaj.nowak.properties_names_generator.annotation.GenerateNames;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Annotation Processor for creating *Names classes for java classes, containing the same properties but with string values representing their names instead of real values.
 * Such names can be then easily used in sql queries, or other places which require to use field names which are mapped to POJO classes.
 * Processor works wit classes annotated with GenerateNames annotation.
 */
@SupportedAnnotationTypes("com.mikolaj.nowak.properties_names_generator.annotation.GenerateNames")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class NamesProcessor extends AbstractProcessor {

    private final static String END_OF_STATEMENT = ";\n";
    private final static String BLOCK_START = "{\n";
    private final static String BLOCK_END = "}\n";

    private final static String ASSIGN = " = ";
    private final static char STRING_VALUE_QUOTE = '\"';

    private final static String PACKAGE = "package ";
    private final static String VALUE_METHOD_NAME = "value";
    private final static String MOST_PROPABLE_ANNOTATION_NAME_METHOD_NAME_PART = "name";

    private final static String NAMES_CLASS_POSTFIX = "Names";

    private final static String CLASS_MODIFIERS = "public final class ";
    private final static String FIELD_MODIFIERS = "     public final static java.lang.String ";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "starts");
        for (TypeElement annotation : annotations) {
            Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
            for (Element annotatedElement : annotatedElements) {
                if (annotatedElement.getKind().isClass()) {
                    processJsonObject(annotatedElement);
                }
            }
        }
        return true;
    }

    private void processJsonObject(Element annotatedElement) {
        GenerateNames generateNamesAnnotation = annotatedElement.getAnnotation(GenerateNames.class);
        Map<String, String> propertiesNames = retrievePropertiesNames(annotatedElement, new Class[0]);//generateNamesAnnotation.nameAnnotations());
        String classNameValue = getClassNameValue(annotatedElement, new Class[0]);//generateNamesAnnotation.nameAnnotations());
        generateNamesClass((TypeElement) annotatedElement, classNameValue, propertiesNames);
    }

    private Map<String, String> retrievePropertiesNames(Element annotatedElement, Class<? extends Annotation>[] nameAnnotations) {
        Map<String, String> propertiesNames = new LinkedHashMap<>();
        for (Element enclosedElement : annotatedElement.getEnclosedElements()) {
            if (enclosedElement.getKind().isField()) {
                addJsonPropertyName(propertiesNames, enclosedElement, nameAnnotations);
            }
        }
        return propertiesNames;
    }

    private void addJsonPropertyName(Map<String, String> propertiesNames, Element propertyElement, Class<? extends Annotation>[] nameAnnotations) {
        String jsonPropertyName = getJsonPropertyName(propertyElement, nameAnnotations);
        propertiesNames.put(propertyElement.getSimpleName().toString(), jsonPropertyName);
    }

    private String getJsonPropertyName(Element propertyElement, Class<? extends Annotation>[] nameAnnotationsClasses) {
        String nameFromAnnotation = getNameFromAnnotation(propertyElement, nameAnnotationsClasses);
        if (nameFromAnnotation != null) {
            return nameFromAnnotation;
        }
        return propertyElement.getSimpleName().toString();
    }

    private String getNameFromAnnotation(Element element, Class<? extends Annotation>[] nameAnnotationsClasses) {
        for (Class<? extends Annotation> nameAnnotationClass : nameAnnotationsClasses) {
            Annotation nameAnnotation = element.getAnnotation(nameAnnotationClass);
            if (nameAnnotation != null) {
                String name = retrieveNameFromAnnotation(nameAnnotationClass, nameAnnotation);
                if (name != null) {
                    return name;
                }
            }
        }
        return null;
    }

    private String retrieveNameFromAnnotation(Class<? extends Annotation> nameAnnotationClass, Annotation nameAnnotation) {
        Method[] methods = nameAnnotationClass.getMethods();
        Method nameMethod = getNameMethod(methods);
        if (nameMethod != null) {
            return tryToGetNameFromAnnotationMethod(nameAnnotation, nameMethod);
        }
        return null;
    }

    private Method getNameMethod(Method[] methods) {
        if (methods.length > 0) {
            if (methods.length > 1) {
                return retrieveNameMethod(methods);
            } else {
                return getOnlyMethodIfReturnsString(methods[0]);
            }
        } else {
            return null;
        }
    }

    private Method retrieveNameMethod(Method[] methods) {
        Method method = retrieveNameMethodByName(methods);
        if (method == null) {
            method = retrieveNameMethodByReturnType(methods);
        }
        return method;
    }

    private Method retrieveNameMethodByName(Method[] methods) {
        for (Method method : methods) {
            if (method.getReturnType().equals(String.class) && method.getName().equals(VALUE_METHOD_NAME)
                    || method.getName().toLowerCase().contains(MOST_PROPABLE_ANNOTATION_NAME_METHOD_NAME_PART)) {
                return method;
            }
        }
        return null;
    }

    private Method retrieveNameMethodByReturnType(Method[] methods) {
        for (Method method : methods) {
            if (method.getReturnType().equals(String.class)) {
                return method;
            }
        }
        return null;
    }

    private Method getOnlyMethodIfReturnsString(Method onlyMethod) {
        if (onlyMethod.getReturnType().equals(String.class)) {
            return onlyMethod;
        } else {
            return null;
        }
    }

    private String tryToGetNameFromAnnotationMethod(Annotation nameAnnotation, Method nameMethod) {
        try {
            return (String) nameMethod.invoke(nameAnnotation);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    private String getClassNameValue(Element annotatedElement, Class<? extends Annotation>[] classes) {
        String jsonName = getNameFromAnnotation(annotatedElement, classes);
        if (jsonName != null) {
            return jsonName;
        }
        return getClassNameAsCamelCase(annotatedElement);
    }

    private String getClassNameAsCamelCase(Element annotatedElement) {
        char[] simpleName = annotatedElement.getSimpleName().toString().toCharArray();
        simpleName[0] = Character.toLowerCase(simpleName[0]);
        return String.valueOf(simpleName);
    }

    private void generateNamesClass(TypeElement annotatedClassElement, String classNameValue, Map<String, String> propertiesNames) {
        try {
            JavaFileObject namedClassFile = processingEnv.getFiler().createSourceFile(
                    getNamesClassName(annotatedClassElement), annotatedClassElement);
            String content = getContent(annotatedClassElement, classNameValue, propertiesNames);
            Writer fileWriter = namedClassFile.openWriter();
            fileWriter.write(content);
            fileWriter.close();
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Couldn't generate Names class for " + annotatedClassElement.getSimpleName());
        }
    }

    private String getNamesClassName(TypeElement annotatedClassElement) {
        return annotatedClassElement.getQualifiedName() + NAMES_CLASS_POSTFIX;
    }

    private String getContent(TypeElement annotatedClassElement, String classNameValue, Map<String, String> propertiesNames) {
        StringBuilder contentBuilder = new StringBuilder();
        appendPackageName(contentBuilder, annotatedClassElement);
        appendClassDefinition(contentBuilder, annotatedClassElement);
        appendClassName(contentBuilder, annotatedClassElement, classNameValue);
        appendProperties(contentBuilder, propertiesNames);
        contentBuilder.append(BLOCK_END);
        return contentBuilder.toString();
    }

    private void appendPackageName(StringBuilder contentBuilder, TypeElement annotatedClassElement) {
        contentBuilder.append(PACKAGE)
                .append(retrievePackageName(annotatedClassElement))
                .append(END_OF_STATEMENT);
    }

    private String retrievePackageName(TypeElement annotatedClassElement) {
        String fullClassName = annotatedClassElement.getQualifiedName().toString();
        int lastDotPos = fullClassName.lastIndexOf('.');
        if (lastDotPos > 0) {
            return fullClassName.substring(0, lastDotPos);
        }
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "Annotated class doesn't have package name");
        return "";
    }

    private void appendClassDefinition(StringBuilder contentBuilder, TypeElement annotatedClassElement) {
        contentBuilder.append(CLASS_MODIFIERS)
                .append(annotatedClassElement.getSimpleName())
                .append(NAMES_CLASS_POSTFIX)
                .append(BLOCK_START);
    }

    private void appendClassName(StringBuilder contentBuilder, TypeElement annotatedClassElement, String classNameValue) {
        String camelCaseClassName = getClassNameAsCamelCase(annotatedClassElement);
        appendProperty(contentBuilder, camelCaseClassName, classNameValue);
    }

    private void appendProperties(StringBuilder contentBuilder, Map<String, String> propertiesNames) {
        for (Map.Entry<String, String> propertyName : propertiesNames.entrySet()) {
            appendProperty(contentBuilder, propertyName.getKey(), propertyName.getValue());
        }
    }

    private void appendProperty(StringBuilder contentBuilder, String namePropertyName, String namePropertyValue) {
        contentBuilder.append(FIELD_MODIFIERS)
                .append(namePropertyName)
                .append(ASSIGN)
                .append(STRING_VALUE_QUOTE)
                .append(namePropertyValue)
                .append(STRING_VALUE_QUOTE)
                .append(END_OF_STATEMENT);
    }
}
