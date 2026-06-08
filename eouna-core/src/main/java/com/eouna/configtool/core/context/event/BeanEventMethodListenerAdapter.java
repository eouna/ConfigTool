package com.eouna.configtool.core.context.event;

import com.eouna.configtool.core.boot.context.ApplicationContext;
import com.eouna.configtool.core.context.ApplicationListener;
import com.eouna.configtool.core.context.PayloadApplicationEvent;
import com.eouna.configtool.core.event.ApplicationEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.UndeclaredThrowableException;

/**
 * @author : [程春林(chengchunlin)]
 * @version : [v1.0]
 * @className : [EventMethodListenerAdapter]
 * @description :  方法事件监听适配器
 * @createTime : [2026/5/12 13:52]
 */
public class BeanEventMethodListenerAdapter implements ApplicationListener<ApplicationEvent> {

    private final String beanName;

    private final Class<?> targetBeanClass;

    private final Method method;

    private Class<? extends ApplicationEvent> methodApplicationEvent;

    private ApplicationContext applicationContext;

    public BeanEventMethodListenerAdapter(String beanName, Class<?> targetBeanClass, Method method) {
        this.beanName = beanName;
        this.targetBeanClass = targetBeanClass;
        this.method = method;
        findQualifierOfApplicationEvent();
    }

    /**
     * 在事件标注的类方法参数中找实现了ApplicationEvent接口的参数
     */
    private void findQualifierOfApplicationEvent() {
        try {
            for (Parameter parameter : method.getParameters()) {
                if (ApplicationEvent.class.isAssignableFrom(parameter.getType())) {
                    methodApplicationEvent = (Class<? extends ApplicationEvent>) parameter.getType();
                    break;
                }
            }
            if (methodApplicationEvent == null) {
                throw new IllegalArgumentException(
                    "类：" + beanName + " 方法：" + method.getName() + " 未找到实现ApplicationEvent" + "接口的参数");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void init(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public Class<? extends ApplicationEvent> getMethodAppEventClass() {
        return methodApplicationEvent;
    }

    @Override
    public void onEventHappen(ApplicationEvent event) {
        java.lang.Object eventSource = event;
        Object bean = applicationContext.getBean(beanName);
        if (bean == null) {
            return;
        }
        try {
            method.setAccessible(true);
            if (event instanceof PayloadApplicationEvent<?> payloadApplicationEvent) {
                Object[] parameters = new Object[method.getParameterCount()];
                int matchCount = 0;
                for (int i = 0; i < method.getParameters().length; i++) {
                    Parameter parameter = method.getParameters()[i];
                    eventSource = payloadApplicationEvent.getSource();
                    if (parameter.getType().isAssignableFrom(eventSource.getClass())) {
                        parameters[i] = eventSource;
                        matchCount++;
                    }
                }
                if (matchCount == method.getParameterCount()) {
                    method.invoke(bean, parameters);
                }
            } else {
                Object[] parameters = new Object[method.getParameterCount()];
                int matchCount = 0;
                for (int i = 0; i < method.getParameters().length; i++) {
                    Parameter parameter = method.getParameters()[i];
                    if (parameter.getType().isAssignableFrom(eventSource.getClass())) {
                        parameters[i] = eventSource;
                        matchCount++;
                    }
                }
                if (matchCount == method.getParameterCount()) {
                    method.invoke(bean, parameters);
                }
            }
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            throw new IllegalStateException(getInvocationErrorMessage(bean, ex.getMessage(),
                new Object[]{eventSource}), ex);
        } catch (InvocationTargetException ex) {
            // Throw underlying exception
            Throwable targetException = ex.getTargetException();
            if (targetException instanceof RuntimeException runtimeException) {
                throw runtimeException;
            } else {
                String msg = getInvocationErrorMessage(bean, "Failed to invoke event listener method",
                    new Object[]{eventSource});
                throw new UndeclaredThrowableException(targetException, msg);
            }
        }
    }

    private String getInvocationErrorMessage(Object bean, String message, Object[] resolvedArgs) {
        StringBuilder sb = new StringBuilder(getDetailedErrorMessage(bean, message));
        sb.append("Resolved arguments: \n");
        for (int i = 0; i < resolvedArgs.length; i++) {
            sb.append('[').append(i).append("] ");
            if (resolvedArgs[i] == null) {
                sb.append("[null] \n");
            } else {
                sb.append("[type=").append(resolvedArgs[i].getClass().getName()).append("] ");
                sb.append("[value=").append(resolvedArgs[i]).append("]\n");
            }
        }
        return sb.toString();
    }

    protected String getDetailedErrorMessage(Object bean, String message) {
        return message + '\n' +
            "HandlerMethod details: \n" +
            "Bean [" + bean.getClass().getName() + "]\n" +
            "Method [" + this.method.toGenericString() + "]\n";
    }
}
