// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.


package org.openqa.selenium.chrome;

import com.google.common.collect.ImmutableMap;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.html5.LocalStorage;
import org.openqa.selenium.html5.Location;
import org.openqa.selenium.html5.LocationContext;
import org.openqa.selenium.html5.SessionStorage;
import org.openqa.selenium.html5.WebStorage;
import org.openqa.selenium.interactions.TouchScreen;
import org.openqa.selenium.mobile.NetworkConnection;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.FileDetector;
import org.openqa.selenium.remote.RemoteTouchScreen;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.Response;
import org.openqa.selenium.remote.html5.RemoteLocationContext;
import org.openqa.selenium.remote.html5.RemoteWebStorage;
import org.openqa.selenium.remote.mobile.RemoteNetworkConnection;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link WebDriver} implementation that controls a Chrome browser running on the local machine.
 * This class is provided as a convenience for easily testing the Chrome browser. The control server
 * which each instance communicates with will live and die with the instance.
 *
 * To avoid unnecessarily restarting the ChromeDriver server with each instance, use a
 * {@link RemoteWebDriver} coupled with the desired {@link ChromeDriverService}, which is managed
 * separately. For example: <pre>{@code
 *
 * import static org.junit.Assert.assertEquals;
 *
 * import org.junit.*;
 * import org.junit.runner.RunWith;
 * import org.junit.runners.JUnit4;
 * import org.openqa.selenium.chrome.ChromeDriverService;
 * import org.openqa.selenium.remote.DesiredCapabilities;
 * import org.openqa.selenium.remote.RemoteWebDriver;
 *
 * {@literal @RunWith(JUnit4.class)}
 * public class ChromeTest extends TestCase {
 *
 *   private static ChromeDriverService service;
 *   private WebDriver driver;
 *
 *   {@literal @BeforeClass}
 *   public static void createAndStartService() {
 *     service = new ChromeDriverService.Builder()
 *         .usingDriverExecutable(new File("path/to/my/chromedriver.exe"))
 *         .usingAnyFreePort()
 *         .build();
 *     service.start();
 *   }
 *
 *   {@literal @AfterClass}
 *   public static void createAndStopService() {
 *     service.stop();
 *   }
 *
 *   {@literal @Before}
 *   public void createDriver() {
 *     driver = new RemoteWebDriver(service.getUrl(),
 *         DesiredCapabilities.chrome());
 *   }
 *
 *   {@literal @After}
 *   public void quitDriver() {
 *     driver.quit();
 *   }
 *
 *   {@literal @Test}
 *   public void testGoogleSearch() {
 *     driver.get("http://www.google.com");
 *     WebElement searchBox = driver.findElement(By.name("q"));
 *     searchBox.sendKeys("webdriver");
 *     searchBox.quit();
 *     assertEquals("webdriver - Google Search", driver.getTitle());
 *   }
 * }
 * }</pre>
 *
 * Note that unlike ChromeDriver, RemoteWebDriver doesn't directly implement
 * role interfaces such as {@link LocationContext} and {@link WebStorage}.
 * Therefore, to access that functionality, it needs to be
 * {@link org.openqa.selenium.remote.Augmenter augmented} and then cast
 * to the appropriate interface.
 *
 * @see ChromeDriverService#createDefaultService
 */
public class ChromeDriver extends RemoteWebDriver
    implements LocationContext, WebStorage {


  private static final Logger logger = Logger.getLogger(ChromeDriver.class.getName());

  private RemoteLocationContext locationContext;
  private RemoteWebStorage webStorage;
  private TouchScreen touchScreen;
  private RemoteNetworkConnection networkConnection;

  /**
   * Creates a new ChromeDriver using the {@link ChromeDriverService#createDefaultService default}
   * server configuration.
   *
   * @see #ChromeDriver(ChromeDriverService, ChromeOptions)
   */
  public ChromeDriver() {
    this(ChromeDriverService.createDefaultService(), new ChromeOptions());
  }

  /**
   * Creates a new ChromeDriver instance. The {@code service} will be started along with the driver,
   * and shutdown upon calling {@link #quit()}.
   *
   * @param service The service to use.
   * @see #ChromeDriver(ChromeDriverService, ChromeOptions)
   */
  public ChromeDriver(ChromeDriverService service) {
    this(service, new ChromeOptions());
  }

  /**
   * Creates a new ChromeDriver instance. The {@code capabilities} will be passed to the
   * chromedriver service.
   *
   * @param capabilities The capabilities required from the ChromeDriver.
   * @see #ChromeDriver(ChromeDriverService, Capabilities)
   */
  public ChromeDriver(Capabilities capabilities) {
    this(ChromeDriverService.createDefaultService(), capabilities);
  }

  /**
   * Creates a new ChromeDriver instance with the specified options.
   *
   * @param options The options to use.
   * @see #ChromeDriver(ChromeDriverService, ChromeOptions)
   */
  public ChromeDriver(ChromeOptions options) {
    this(ChromeDriverService.createDefaultService(), options);
  }

  /**
   * Creates a new ChromeDriver instance with the specified options. The {@code service} will be
   * started along with the driver, and shutdown upon calling {@link #quit()}.
   *
   * @param service The service to use.
   * @param options The options to use.
   */
  public ChromeDriver(ChromeDriverService service, ChromeOptions options) {
    this(service, options.toCapabilities());
  }

  /**
   * Creates a new ChromeDriver instance. The {@code service} will be started along with the
   * driver, and shutdown upon calling {@link #quit()}.
   *
   * @param service The service to use.
   * @param capabilities The capabilities required from the ChromeDriver.
   */
  public ChromeDriver(ChromeDriverService service, Capabilities capabilities) {
    super(new ChromeDriverCommandExecutor(service), capabilities);
    locationContext = new RemoteLocationContext(getExecuteMethod());
    webStorage = new  RemoteWebStorage(getExecuteMethod());
    this.touchScreen = new RemoteTouchScreen(this.getExecuteMethod());
    this.networkConnection = new RemoteNetworkConnection(this.getExecuteMethod());
  }

  @Override
  public void setFileDetector(FileDetector detector) {
    throw new WebDriverException(
        "Setting the file detector only works on remote webdriver instances obtained " +
        "via RemoteWebDriver");
  }

  @Override
  public LocalStorage getLocalStorage() {
    return webStorage.getLocalStorage();
  }

  @Override
  public SessionStorage getSessionStorage() {
    return webStorage.getSessionStorage();
  }

  @Override
  public Location location() {
    return locationContext.location();
  }

  @Override
  public void setLocation(Location location) {
    locationContext.setLocation(location);
  }

  public TouchScreen getTouch() {
      return this.touchScreen;
  }

  public NetworkConnection.ConnectionType getNetworkConnection() {
      return this.networkConnection.getNetworkConnection();
  }

  public NetworkConnection.ConnectionType setNetworkConnection(NetworkConnection.ConnectionType type) {
      return this.networkConnection.setNetworkConnection(type);
  }

  /**
   * Launches Chrome app specified by id.
   *
   * @param id chrome app id
   */
  public void launchApp(String id) {
    execute(ChromeDriverCommand.LAUNCH_APP, ImmutableMap.of("id", id));
  }


  public <X> X getScreenshotAs(OutputType<X> outputType) throws WebDriverException {
    try {
      Object
          visibleSize =
          evaluate("({x:0,y:0,width:window.innerWidth,height:window.innerHeight})");
      Long visibleW = jsonValue(visibleSize, "result.value.width", Long.class);
      Long visibleH = jsonValue(visibleSize, "result.value.height", Long.class);

      Object contentSize = send("Page.getLayoutMetrics", new HashMap<String, Object>());
      Long cw = jsonValue(contentSize, "contentSize.width", Long.class);
      Long ch = jsonValue(contentSize, "contentSize.height", Long.class);

              /*
               * In chrome 61, delivered one day after I wrote this comment, the method forceViewport was removed.
               * I commented it out here with the if(false), and hopefully wrote a working alternative in the else 8-/
               */
      Map<String, Object> parms;

      if (false) {
        parms = new HashMap<>();
        parms.put("width", cw);
        parms.put("height", ch);
        send("Emulation.setVisibleSize", parms);

        parms = new HashMap<>();
        parms.put("x", Long.valueOf(0));
        parms.put("y", Long.valueOf(0));
        parms.put("scale", Long.valueOf(1));

        send("Emulation.forceViewport", parms);
      } else {
        parms = new HashMap<>();
        parms.put("width", cw);
        parms.put("height", ch);
        parms.put("deviceScaleFactor", Long.valueOf(1));
        parms.put("mobile", Boolean.FALSE);
        parms.put("fitWindow", Boolean.FALSE);
        send("Emulation.setDeviceMetricsOverride", parms);

        parms = new HashMap<>();
        parms.put("width", cw);
        parms.put("height", ch);
        send("Emulation.setVisibleSize", parms);
      }


      parms = new HashMap<>();
      parms.put("format", "png");
      parms.put("fromSurface", Boolean.TRUE);
      Object value = send("Page.captureScreenshot", parms);

      // Since chrome 61 this call has disappeared too; it does not seem to be necessary anymore with the new code.
      // send("Emulation.resetViewport", ImmutableMap.of());

      parms = new HashMap<>();
      parms.put("x", 0L);
      parms.put("y", 0L);
      parms.put("width", visibleW);
      parms.put("height", visibleH);
      send("Emulation.setVisibleSize", parms);

      String image = jsonValue(value, "data", String.class);
      byte[] bytes = Base64.getDecoder().decode(image);
      return outputType.convertFromPngBytes(bytes);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Could not take screenshot", e);
    }
    return null;
  }



  private Object evaluate(String script) throws IOException {
      Map<String, Object> param = new HashMap<>();
      param.put("returnByValue", Boolean.TRUE);
      param.put("expression", script);

      return send("Runtime.evaluate", param);
  }

  private Object send(String cmd, Map<String, Object> params) throws IOException {
      Map<String, Object> exe = ImmutableMap.of("cmd", cmd, "params", params);
      Command xc = new Command(getSessionId(), "sendCommandWithResult", exe);
      Response response = getCommandExecutor().execute(xc);

      Object value = response.getValue();
      if(response.getStatus() == null || response.getStatus().intValue() != 0) {
          //System.out.println("resp: " + response);
          throw new WebDriverException("Command '" + cmd + "' failed: " + value);
      }
      if(null == value)
          throw new WebDriverException("Null response value to command '" + cmd + "'");
      //System.out.println("resp: " + value);
      return value;
  }

  static private <T> T jsonValue(Object map, String path, Class<T> type) {
      String[] segs = path.split("\\.");
      Object current = map;
      for(String name: segs) {
          Map<String, Object> cm = (Map<String, Object>) current;
          Object o = cm.get(name);
          if(null == o)
              return null;
          current = o;
      }
      return (T) current;
  }
}
