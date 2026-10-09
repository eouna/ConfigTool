package com.eouna.configtool.core.factory.bean;

import java.lang.reflect.Type;
import java.util.Objects;

/**
 * @author CCL
 * @version : [v1.0]
 * @className : [Resource]
 * @description :  bean资源
 * @createTime : [2026/5/9 17:52]
 */
public class BeanDefinitionResource {

    /**
     * 资源类型（field, parameter）ref class
     */
    private Type resourceType;

    /**
     * 资源实例
     */
    private Object resourceRef;

    /**
     * 资源名
     */
    private String resourceName;

    public Type getResourceType() {
        return resourceType;
    }

    public void setResourceType(Type resourceType) {
        this.resourceType = resourceType;
    }

    public Object getResourceRef() {
        return resourceRef;
    }

    public void setResourceRef(Object resourceRef) {
        this.resourceRef = resourceRef;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        BeanDefinitionResource that = (BeanDefinitionResource) o;
        return Objects.equals(resourceType, that.resourceType) && Objects.equals(resourceRef, that.resourceRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceType, resourceRef);
    }
}
