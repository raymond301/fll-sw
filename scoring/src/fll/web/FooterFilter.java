/*
 * Copyright (c) 2010 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */

package fll.web;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.util.Formatter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import fll.Version;

/**
 * Ensure that all HTML pages get the same footer.
 */
public class FooterFilter implements Filter {

  private static final Logger LOGGER = Logger.getLogger(FooterFilter.class);

  /**
   * @see javax.servlet.Filter#destroy()
   */
  public void destroy() {
    filterConfig = null;
  }

  /**
   * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
   *      javax.servlet.ServletResponse, javax.servlet.FilterChain)
   */
  public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
    if (response instanceof HttpServletResponse && request instanceof HttpServletRequest) {
      final HttpServletResponse httpResponse = (HttpServletResponse)response;
      final HttpServletRequest httpRequest = (HttpServletRequest)request;
      final CharResponseWrapper wrapper = new CharResponseWrapper(httpResponse);
      chain.doFilter(request, wrapper);
      
      
      final String origData = wrapper.getString();
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(new Formatter().format("page: %s content type: %s ###%s###", httpRequest.getRequestURL(), wrapper.getContentType(), origData));
      }
      
      final ServletOutputStream out = response.getOutputStream();
      if ("text/html".equals(wrapper.getContentType())) {
        final CharArrayWriter caw = new CharArrayWriter();
        final int bodyIndex = origData.indexOf("</body>");
        if (-1 != bodyIndex) {
          caw.write(origData.substring(0, bodyIndex - 1));
          addFooter(caw, httpRequest);
          response.setContentLength(caw.toString().length());
          out.print(caw.toString());
        } else {
          out.print(origData);
        }
      } else {
        out.print(origData);
      }
      out.close();
    } else {
      chain.doFilter(request, response);
    }
  }

  /**
   * Writer the footer to the char array writer.
   */
  private static void addFooter(final CharArrayWriter caw, final HttpServletRequest request) throws IOException {
    final String contextPath = request.getContextPath();
    final Formatter formatter = new Formatter(caw);
    formatter.format("<hr />");
    formatter.format("<table>");
    formatter.format("  <tr>");
    formatter.format("    <td><a href='%s/index.jsp' target='_top'>Main Index</a></td>", contextPath);
    formatter.format("    <td><a href='%s/admin/index.jsp' target='_top'>Admin Index</a></td>", contextPath);
    formatter.format("  </tr>");
    formatter.format("  <tr><td>Software version: %s</td></tr>", Version.getVersion());
    formatter.format("</table>");    
    formatter.format("\n</body></html>");
  }

  /**
   * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
   */
  public void init(final FilterConfig filterConfig) throws ServletException {
    this.filterConfig = filterConfig;
  }

  private FilterConfig filterConfig;
}
