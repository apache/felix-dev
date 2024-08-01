package org.apache.felix.http.javaxwrappers;


import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.jetbrains.annotations.NotNull;

/**
 * Javax Filter based on a jakarta filter
 */
public class FilterWrapper implements Filter
{

    private final jakarta.servlet.Filter filter;


    /**
     * Create new filter
     *
     * @param filter wrapped filter
     */
    public FilterWrapper(@NotNull final jakarta.servlet.Filter filter)
    {
        this.filter = filter;
    }


    @Override
    public void init(final FilterConfig filterConfig) throws ServletException
    {
        try
        {
            this.filter.init(new org.apache.felix.http.jakartawrappers.FilterConfigWrapper(filterConfig));
        }
        catch (final jakarta.servlet.ServletException e)
        {
            throw ServletExceptionUtil.getServletException(e);
        }
    }


    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException
    {
        try
        {
            this.filter.doFilter(org.apache.felix.http.jakartawrappers.ServletRequestWrapper.getWrapper(request),
                                 org.apache.felix.http.jakartawrappers.ServletResponseWrapper.getWrapper(response),
                                 new org.apache.felix.http.jakartawrappers.FilterChainWrapper(chain));
        }
        catch (final jakarta.servlet.ServletException e)
        {
            throw ServletExceptionUtil.getServletException(e);
        }
    }


    @Override
    public void destroy()
    {
        this.filter.destroy();
    }


    /**
     * Get the filter
     *
     * @return The filter
     */
    public @NotNull jakarta.servlet.Filter getFilter()
    {
        return this.filter;
    }
}
