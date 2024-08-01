package org.apache.felix.http.jakartawrappers;


import java.io.IOException;

import javax.servlet.FilterChain;

import org.apache.felix.http.javaxwrappers.ServletRequestWrapper;
import org.apache.felix.http.javaxwrappers.ServletResponseWrapper;
import org.jetbrains.annotations.NotNull;

/**
 * Jakarta filter chain based on a javax filter chain
 */
public class FilterChainWrapper implements jakarta.servlet.FilterChain
{
    private final FilterChain filterChain;


    /**
     * Create new chain
     *
     * @param chain Wrapped chain
     */
    public FilterChainWrapper(@NotNull final FilterChain chain)
    {
        this.filterChain = chain;
    }


    @Override
    public void doFilter(final jakarta.servlet.ServletRequest request, final jakarta.servlet.ServletResponse response)
                    throws IOException, jakarta.servlet.ServletException
    {
        try
        {
            filterChain.doFilter(ServletRequestWrapper.getWrapper(request),
                                 ServletResponseWrapper.getWrapper(response));
        }
        catch (final javax.servlet.ServletException e)
        {
            throw ServletExceptionUtil.getServletException(e);
        }
    }

}
