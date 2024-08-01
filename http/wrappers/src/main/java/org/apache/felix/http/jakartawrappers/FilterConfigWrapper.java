package org.apache.felix.http.jakartawrappers;


import java.util.Enumeration;

import javax.servlet.FilterConfig;

import org.jetbrains.annotations.NotNull;


/**
 * Filter config wrapper
 */
public class FilterConfigWrapper implements jakarta.servlet.FilterConfig
{

    private final FilterConfig filterConfig;


    /**
     * Create config
     *
     * @param filterConfig wrapped config
     */
    public FilterConfigWrapper(@NotNull final FilterConfig filterConfig)
    {
        this.filterConfig = filterConfig;
    }


    @Override
    public String getFilterName()
    {
        return this.filterConfig.getFilterName();
    }


    @Override
    public jakarta.servlet.ServletContext getServletContext()
    {
        return new ServletContextWrapper(this.filterConfig.getServletContext());
    }


    @Override
    public String getInitParameter(final String name)
    {
        return this.filterConfig.getInitParameter(name);
    }


    @Override
    public Enumeration<String> getInitParameterNames()
    {
        return this.filterConfig.getInitParameterNames();
    }
}
