/*
 * Copyright (c) 2013 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */

package fll.xml;

import java.io.Serializable;

import org.w3c.dom.Element;

import fll.util.FLLInternalException;

/**
 * Common elements of {@link Term} and {@link VariableTerm}.
 */
public abstract class AbstractTerm implements Evaluatable, Serializable {

  public AbstractTerm(final Element ele) {
    mCoefficient = Double.valueOf(ele.getAttribute("coefficient"));
    mFloatingPoint = FloatingPointType.fromString(ele.getAttribute("floatingPoint"));
  }

  private final double mCoefficient;

  public double getCoefficient() {
    return mCoefficient;
  }

  private final FloatingPointType mFloatingPoint;

  public FloatingPointType getFloatingPoint() {
    return mFloatingPoint;
  }

  protected final double applyFloatingPointType(final double value) {
    switch (getFloatingPoint()) {
    case DECIMAL:
      return value;
    case ROUND:
      return Math.round(value);
    case TRUNCATE:
      return (double) ((long) value);
    default:
      throw new FLLInternalException("Unknown floating point type: "
          + getFloatingPoint());
    }
  }

}
