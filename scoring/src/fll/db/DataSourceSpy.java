/*
 * Copyright (c) 2010 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */

package fll.db;

import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

import net.sf.log4jdbc.ConnectionSpy;

/**
 * Based upon code from the bug report
 * http://code.google.com/p/log4jdbc/issues/detail?id=6
 */
public class DataSourceSpy implements DataSource, Serializable {
  private DataSource realDataSource;

  private boolean enabled = Boolean.parseBoolean(System.getProperty("log4jdbc.enabled", "true"));

  public DataSourceSpy() {
  }

  public DataSourceSpy(final DataSource realDataSource) {
    this.realDataSource = realDataSource;
  }

  public void setRealDataSource(final DataSource realDataSource) {
    this.realDataSource = realDataSource;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public Connection getConnection() throws SQLException {
    if (enabled) {
      return new ConnectionSpy(this.realDataSource.getConnection());
    } else {
      return this.realDataSource.getConnection();
    }
  }

  @Override
  public Connection getConnection(final String username,
                                  final String password) throws SQLException {
    if (enabled) {
      return new ConnectionSpy(realDataSource.getConnection(username, password));
    } else {
      return realDataSource.getConnection(username, password);
    }
  }

  @Override
  public int getLoginTimeout() throws SQLException {
    return realDataSource.getLoginTimeout();
  }

  @Override
  public PrintWriter getLogWriter() throws SQLException {
    return realDataSource.getLogWriter();
  }

  @Override
  public boolean isWrapperFor(final Class<?> iface) throws SQLException {
    return realDataSource.isWrapperFor(iface);
  }

  @Override
  public void setLoginTimeout(final int seconds) throws SQLException {
    realDataSource.setLoginTimeout(seconds);
  }

  @Override
  public void setLogWriter(final PrintWriter out) throws SQLException {
    realDataSource.setLogWriter(out);
  }

  @Override
  public <T> T unwrap(final Class<T> iface) throws SQLException {
    return realDataSource.unwrap(iface);
  }
  
  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return realDataSource.getParentLogger();
  }

}