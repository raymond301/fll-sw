/*
 * Copyright (c) 2010 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */

package fll.web;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.Select;

import fll.TestUtils;
import fll.util.LogUtils;

/**
 * Some utilities for integration tests.
 */
public final class IntegrationTestUtils {

  private static final Logger LOGGER = LogUtils.getLogger();

  public static final String WAIT_FOR_PAGE_TIMEOUT = "60000";

  public static final String TEST_USERNAME = "fll";

  public static final String TEST_PASSWORD = "Lego";

  private IntegrationTestUtils() {
    // no instances
  }

  /**
   * Check if an element exists.
   */
  public static boolean isElementPresent(final WebDriver selenium,
                                         final By search) {
    boolean elementFound = false;
    try {
      selenium.findElement(search);
      elementFound = true;
    } catch (NoSuchElementException e) {
      elementFound = false;
    }
    return elementFound;
  }

  /**
   * Load a page and check to make sure the page didn't crash.
   * 
   * @param selenium the test controller
   * @param url the page to load
   * @throws IOException
   */
  public static void loadPage(final WebDriver selenium,
                              final String url) throws IOException {
    try {
      selenium.get(url);

      assertNoException(selenium);
    } catch (final AssertionError e) {
      IntegrationTestUtils.storeScreenshot(selenium);
      throw e;
    } catch (final RuntimeException e) {
      IntegrationTestUtils.storeScreenshot(selenium);
      throw e;
    }
  }

  public static void assertNoException(final WebDriver selenium) {
    Assert.assertFalse("Error loading page", isElementPresent(selenium, By.id("exception-handler")));
  }

  /**
   * Initialize the database using the given challenge descriptor.
   * 
   * @param driver the test controller
   * @param challengeStream the challenge descriptor
   * @throws IOException
   */
  public static void initializeDatabase(final WebDriver driver,
                                        final InputStream challengeStream) throws IOException {
    try {
      Assert.assertNotNull(challengeStream);
      final File challengeFile = IntegrationTestUtils.storeInputStreamToFile(challengeStream);
      try {
        driver.get(TestUtils.URL_ROOT
            + "setup/");

        if (isElementPresent(driver, By.name("submit_login"))) {
          login(driver);

          driver.get(TestUtils.URL_ROOT
              + "setup/");
        }

        final WebElement fileEle = driver.findElement(By.name("xmldocument"));
        fileEle.sendKeys(challengeFile.getAbsolutePath());

        final WebElement reinitDB = driver.findElement(By.name("reinitializeDatabase"));
        reinitDB.click();

        final Alert confirmCreateDB = driver.switchTo().alert();
        LOGGER.info("Confirmation text: "
            + confirmCreateDB.getText());
        confirmCreateDB.accept();

        driver.findElement(By.id("success"));

        // setup user
        final WebElement userElement = driver.findElement(By.name("user"));
        userElement.sendKeys(TEST_USERNAME);

        final WebElement passElement = driver.findElement(By.name("pass"));
        passElement.sendKeys(TEST_PASSWORD);

        final WebElement passCheckElement = driver.findElement(By.name("pass_check"));
        passCheckElement.sendKeys(TEST_PASSWORD);

        final WebElement submitElement = driver.findElement(By.name("submit_create_user"));
        submitElement.click();

        driver.findElement(By.id("success-create-user"));

        login(driver);
      } finally {
        if (!challengeFile.delete()) {
          challengeFile.deleteOnExit();
        }
      }
    } catch (final AssertionError e) {
      IntegrationTestUtils.storeScreenshot(driver);
      throw e;
    } catch (final RuntimeException e) {
      IntegrationTestUtils.storeScreenshot(driver);
      throw e;
    } catch (final IOException e) {
      IntegrationTestUtils.storeScreenshot(driver);
      throw e;
    }
  }

  /**
   * Initialize a database from a zip file.
   * 
   * @param selenium the test controller
   * @param inputStream input stream that has database to load in it, this input
   *          stream is closed by this method upon successful completion
   * @throws IOException
   */
  public static void initializeDatabaseFromDump(final WebDriver selenium,
                                                final InputStream inputStream) throws IOException {
    try {
      Assert.assertNotNull(inputStream);
      final File dumpFile = IntegrationTestUtils.storeInputStreamToFile(inputStream);
      try {
        selenium.get(TestUtils.URL_ROOT
            + "setup/");

        if (isElementPresent(selenium, By.name("submit_login"))) {
          login(selenium);

          selenium.get(TestUtils.URL_ROOT
              + "setup/");
        }

        final WebElement dbEle = selenium.findElement(By.name("dbdump"));
        dbEle.sendKeys(dumpFile.getAbsolutePath());

        final WebElement createEle = selenium.findElement(By.name("createdb"));
        createEle.click();

        selenium.findElement(By.id("success"));

        // setup user
        final WebElement userElement = selenium.findElement(By.name("user"));
        userElement.sendKeys(TEST_USERNAME);

        final WebElement passElement = selenium.findElement(By.name("pass"));
        passElement.sendKeys(TEST_PASSWORD);

        final WebElement passCheckElement = selenium.findElement(By.name("pass_check"));
        passCheckElement.sendKeys(TEST_PASSWORD);

        final WebElement submitElement = selenium.findElement(By.name("submit_create_user"));
        submitElement.click();

        selenium.findElement(By.id("success-create-user"));

        login(selenium);
      } finally {
        if (!dumpFile.delete()) {
          dumpFile.deleteOnExit();
        }
      }
      login(selenium);
    } catch (final AssertionError e) {
      IntegrationTestUtils.storeScreenshot(selenium);
      throw e;
    } catch (final RuntimeException e) {
      IntegrationTestUtils.storeScreenshot(selenium);
      throw e;
    } catch (final IOException e) {
      IntegrationTestUtils.storeScreenshot(selenium);
      throw e;
    }
  }

  public static void storeScreenshot(final WebDriver driver) throws IOException {
    final File baseFile = File.createTempFile("fll", null, new File("screenshots"));

    final File screenshot = new File(baseFile.getAbsolutePath()
        + ".png");
    if (driver instanceof TakesScreenshot) {
      final File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
      FileUtils.copyFile(scrFile, screenshot);
      LOGGER.error("Screenshot saved to "
          + screenshot.getAbsolutePath());
    } else {
      LOGGER.warn("Unable to get screenshot");
    }

    final File htmlFile = new File(baseFile.getAbsolutePath()
        + ".html");
    final String html = driver.getPageSource();
    final FileWriter writer = new FileWriter(htmlFile);
    writer.write(html);
    writer.close();
    LOGGER.error("HTML saved to "
        + htmlFile.getAbsolutePath());

  }

  /**
   * Copy the contents of a stream to a temporary file.
   * 
   * @param inputStream the data to store in the temporary file
   * @return the temporary file, you need to delete it
   * @throws IOException
   */
  public static File storeInputStreamToFile(final InputStream inputStream) throws IOException {
    final File tempFile = File.createTempFile("fll", null);
    final FileOutputStream outputStream = new FileOutputStream(tempFile);
    final byte[] buffer = new byte[1042];
    int bytesRead;
    while (-1 != (bytesRead = inputStream.read(buffer))) {
      outputStream.write(buffer, 0, bytesRead);
    }
    outputStream.close();

    return tempFile;
  }

  /**
   * Login to fll
   */
  public static void login(final WebDriver driver) {
    driver.get(TestUtils.URL_ROOT
        + "login.jsp");

    final WebElement userElement = driver.findElement(By.name("user"));
    userElement.sendKeys(TEST_USERNAME);

    final WebElement passElement = driver.findElement(By.name("pass"));
    passElement.sendKeys(TEST_PASSWORD);

    final WebElement submitElement = driver.findElement(By.name("submit_login"));
    submitElement.click();
  }

  /**
   * Add a team to a tournament.
   */
  public static void addTeam(final WebDriver selenium,
                             final int teamNumber,
                             final String teamName,
                             final String organization,
                             final String division,
                             final String tournament) throws IOException {
    try {
      loadPage(selenium, TestUtils.URL_ROOT
          + "admin/index.jsp");

      selenium.findElement(By.linkText("Add a team")).click();

      selenium.findElement(By.name("teamNumber")).sendKeys(String.valueOf(teamNumber));
      selenium.findElement(By.name("teamName")).sendKeys(teamName);
      selenium.findElement(By.name("organization")).sendKeys(organization);
      selenium.findElement(By.id("division_text_choice")).click();
      selenium.findElement(By.name("division_text")).sendKeys(division);

      final WebElement currentTournament = selenium.findElement(By.id("currentTournamentSelect"));
      final Select currentTournamentSel = new Select(currentTournament);
      String tournamentID = null;
      for (final WebElement option : currentTournamentSel.getOptions()) {
        final String text = option.getText();
        if (text.equals(tournament)) {
          tournamentID = option.getAttribute("value");
        }
      }
      Assert.assertNotNull("Could not find tournament with name: "
          + tournament, tournamentID);

      currentTournamentSel.selectByValue(tournamentID);

      selenium.findElement(By.name("commit")).click();

      selenium.findElement(By.id("success"));

    } catch (final AssertionError e) {
      IntegrationTestUtils.storeScreenshot(selenium);
      throw e;
    } catch (final RuntimeException e) {
      IntegrationTestUtils.storeScreenshot(selenium);
      throw e;
    } catch (final IOException e) {
      IntegrationTestUtils.storeScreenshot(selenium);
      throw e;
    }

  }

  /**
   * Set the current tournament by name.
   * 
   * @param tournamentName the name of the tournament to make the current
   *          tournament
   * @throws IOException
   */
  public static void setTournament(final WebDriver selenium,
                                   final String tournamentName) throws IOException {
    try {
      loadPage(selenium, TestUtils.URL_ROOT
          + "admin/index.jsp");

      final WebElement currentTournament = selenium.findElement(By.id("currentTournamentSelect"));

      final Select currentTournamentSel = new Select(currentTournament);
      String tournamentID = null;
      for (final WebElement option : currentTournamentSel.getOptions()) {
        final String text = option.getText();
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("setTournament option: "
              + text);
        }
        if (text.endsWith("[ "
            + tournamentName + " ]")) {
          tournamentID = option.getAttribute("value");
        }
      }
      Assert.assertNotNull("Could not find tournament with name: "
          + tournamentName, tournamentID);

      currentTournamentSel.selectByValue(tournamentID);

      final WebElement changeTournament = selenium.findElement(By.name("change_tournament"));
      changeTournament.click();

      Assert.assertNotNull(selenium.findElement(By.id("success")));
    } catch (final AssertionError e) {
      IntegrationTestUtils.storeScreenshot(selenium);
      throw e;
    } catch (final RuntimeException e) {
      IntegrationTestUtils.storeScreenshot(selenium);
      throw e;
    } catch (final IOException e) {
      IntegrationTestUtils.storeScreenshot(selenium);
      throw e;
    }
  }

  /**
   * Create a web driver and set appropriate timeouts on it.
   */
  public static WebDriver createWebDriver() {
    final WebDriver selenium = new FirefoxDriver();
    selenium.manage().timeouts().implicitlyWait(500, TimeUnit.MILLISECONDS);
    selenium.manage().timeouts().pageLoadTimeout(30, TimeUnit.SECONDS);
    return selenium;
  }

  public static void initializePlayoffsForDivision(final WebDriver selenium,
                                                   final String division) throws IOException {
    loadPage(selenium, TestUtils.URL_ROOT
        + "playoff");

    final Select initDiv = new Select(selenium.findElement(By.id("initialize-division")));
    initDiv.selectByValue(division);
    selenium.findElement(By.id("initialize_brackets")).click();
    Assert.assertFalse("Error loading page", isElementPresent(selenium, By.id("exception-handler")));

  }

  /**
   * Try harder to find elements.
   */
  public static WebElement findElement(final WebDriver selenium,
                                       final By by,
                                       final int maxAttempts) {
    int attempts = 0;
    WebElement e = null;
    while (e == null
        && attempts <= maxAttempts) {
      try {
        e = selenium.findElement(by);
      } catch (final NoSuchElementException ex) {
        ++attempts;
        e = null;
        if (attempts >= maxAttempts) {
          throw ex;
        } else {
          LOGGER.warn("Trouble finding element, trying again", ex);
        }
      }
    }

    return e;
  }

}
