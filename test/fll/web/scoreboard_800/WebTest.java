/*
 * Copyright (c) 2000-2003 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */
package fll.web.scoreboard_800;

import java.io.IOException;
import java.net.MalformedURLException;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

import fll.TestUtils;

/**
 * Basic tests.
 *
 * @version $Revision$
 */
public class WebTest extends TestCase {
  
  /**
   * Basic load of the pages.
   */
  public void testPages()
    throws SAXException, MalformedURLException, IOException {
    final String[] pages = new String[] {
      //"main.jsp",
    };
    final WebConversation conversation = new WebConversation();
    for(int i=0; i<pages.length; i++) {
      final WebRequest request = new GetMethodWebRequest(TestUtils.URL_ROOT + "scoreboard_800/" + pages[i]);
      final WebResponse response = conversation.getResponse(request);
      Assert.assertTrue(response.isHTML());
    }
  }
  
}
