package org.osgi.util.converter;

public interface InterfaceWithDefaultMethod
{
    public static final String RESULT = "r";

    public default String defaultMethod()
    {
        return RESULT;
    }
}