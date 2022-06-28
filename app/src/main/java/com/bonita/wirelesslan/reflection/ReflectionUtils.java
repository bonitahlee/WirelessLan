package com.bonita.wirelesslan.reflection;

import java.lang.reflect.Method;

/**
 * Reflection 관련 기능을 모아놓은 클래스
 *
 * @author bonita
 * @date 2020.10.19
 */
public class ReflectionUtils {

    private ReflectionUtils() {/* Nothing to do */}

    /**
     * Reflection 으로 inner 클래스 반환
     */
    public static Class<?> getInnerClass(final String a_outerClassName, final String a_innerClassName) {
        try {
            // class 내부 클래스 찾기
            final Class<?> outerClass = Class.forName(a_outerClassName);
            final Class<?>[] classes = outerClass.getDeclaredClasses();

            for (Class<?> declaredClass : classes) {
                if (declaredClass.getName().endsWith(a_innerClassName) == true) {
                    return declaredClass;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Reflection 클래스에서 static field 의 값을 반환
     */
    public static Object getStaticFieldValue(final Class<?> a_reflectionClass,
                                             final String a_field) throws Exception {
        return a_reflectionClass.getField(a_field).get(null);
    }

    /**
     * Reflection 클래스에서 public field 의 값을 반환
     */
    public static Object getPublicFieldValue(final Object a_classInstance,
                                             final String a_field) throws Exception {
        return a_classInstance.getClass().getField(a_field).get(a_classInstance);
    }

    /**
     * Reflection 클래스에서 public field 의 값을 setting
     */
    public static void setPublicFieldValue(final Object a_classInstance,
                                           final String a_field, final Object a_value) throws Exception {
        a_classInstance.getClass().getDeclaredField(a_field).set(a_classInstance, a_value);
    }

    /**
     * 해당 클래스의 getter/setter method 의 값 반환
     */
    public static Object getMethodValue(final Object a_obj, final String a_method) {
        try {
            final Class<?> reflectionClass = a_obj.getClass();
            final Method method = reflectionClass.getMethod(a_method, null);

            return method.invoke(a_obj);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}