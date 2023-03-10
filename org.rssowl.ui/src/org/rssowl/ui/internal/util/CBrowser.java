/*   **********************************************************************  **
 **   Copyright notice                                                       **
 **                                                                          **
 **   (c) 2005-2009 RSSOwl Development Team                                  **
 **   http://www.rssowl.org/                                                 **
 **                                                                          **
 **   All rights reserved                                                    **
 **                                                                          **
 **   This program and the accompanying materials are made available under   **
 **   the terms of the Eclipse Public License v1.0 which accompanies this    **
 **   distribution, and is available at:                                     **
 **   http://www.rssowl.org/legal/epl-v10.html                               **
 **                                                                          **
 **   A copy is found in the file epl-v10.html and important notices to the  **
 **   license from the team is found in the textfile LICENSE.txt distributed **
 **   in this package.                                                       **
 **                                                                          **
 **   This copyright notice MUST APPEAR in all copies of the file!           **
 **                                                                          **
 **   Contributors:                                                          **
 **     RSSOwl Development Team - initial API and implementation             **
 **                                                                          **
 **  **********************************************************************  */

package org.rssowl.ui.internal.util;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.OpenWindowListener;
import org.eclipse.swt.browser.WindowEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.ui.PlatformUI;
import org.rssowl.core.Owl;
import org.rssowl.core.internal.persist.pref.DefaultPreferences;
import org.rssowl.core.persist.pref.IPreferenceScope;
import org.rssowl.core.util.StringUtils;
import org.rssowl.core.util.URIUtils;
import org.rssowl.ui.internal.Activator;
import org.rssowl.ui.internal.Application;
import org.rssowl.ui.internal.ApplicationServer;
import org.rssowl.ui.internal.ApplicationWorkbenchWindowAdvisor;
import org.rssowl.ui.internal.ILinkHandler;
import org.rssowl.ui.internal.OwlUI;
import org.rssowl.ui.internal.editors.browser.WebBrowserContext;
import org.rssowl.ui.internal.editors.browser.WebBrowserView;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * Instances of <code>CBrowser</code> wrap around the Browser-Widget and enhance
 * it by some means.
 *
 * @author bpasero
 */
public class CBrowser {

  /* Delay before a URL is set in a IE/Mozilla Browser (guess) */
  private static final int BROWSER_URL_DELAY = 500;

  /* JavaScript: print() Method */
  private static final String JAVA_SCRIPT_PRINT = "window.print();"; //$NON-NLS-1$

  /* System Properties to configure proxy with XULRunner */
  private static final String XULRUNNER_PROXY_HOST = "network.proxy_host"; //$NON-NLS-1$
  private static final String XULRUNNER_PROXY_PORT = "network.proxy_port"; //$NON-NLS-1$

  /* Delay in millies after a refresh until to allow ext. navigation again (see Bug 1429) */
  private static final long REFRESH_NAVIGATION_DELAY = Application.IS_MAC ? 1000 : 800;

  /* Mac only: Delay in millies after a URL change until to allow ext. navigation again (see Bug 1350) */
  private static final long URL_CHANGE_NAVIGATION_DELAY = 1000;

  /* Flag to check if Mozilla is available on Windows */
  private static boolean fgMozillaAvailable = true;

  /* Flag to check if Mozilla is running as Browser */
  private static boolean fgMozillaRunningOnWindows;

  /* Flag to check if RSSOwl failed to set a Feature in IE */
  private static boolean fgCoInternetSetFeatureError;

  /* Track if the Navigation Sound Disable Feature was set */
  private static boolean fgNavigationSoundsDisabled;

  /* Track if the Popup Blocker Feature was set */
  private static boolean fgPopupBlockerEnabled;

  /* Windows IE Only Features (CoInternetSetFeatureEnabled) */
  private static final int FEATURE_WEBOC_POPUPMANAGEMENT = 5;
  private static final int FEATURE_SECURITYBAND = 9;
  private static final int FEATURE_DISABLE_NAVIGATION_SOUNDS = 21;
  private static final int SET_FEATURE_ON_PROCESS = 0x2;

  private Browser fBrowser;
  private boolean fAllowExternalNavigation;
  private long fLastRefresh;
  private long fLastUrlChange;
  private IPreferenceScope fPreferences;
  private IPreferenceScope fEclipsePreferences;
  private Map<String, ILinkHandler> fLinkHandler;
  private String fLastSetUrl;

  /**
   * @param parent The Parent Composite of this Browser.
   * @param style The Style to use for the Browser-
   */
  public CBrowser(Composite parent, int style) {
    fPreferences = Owl.getPreferenceService().getGlobalScope();
    fEclipsePreferences = Owl.getPreferenceService().getEclipseScope();
    try {
      fBrowser = createBrowser(parent, style);
    } catch (SWTError e) {
      MessageBox box = new MessageBox(parent.getShell(), SWT.ICON_ERROR | SWT.OK | SWT.CANCEL);
      box.setText(Messages.CBrowser_ERROR_CREATE_BROWSER);
      box.setMessage(Messages.CBrowser_ERROR_CREATE_BROWSER_MSG);
      if (box.open() == SWT.OK)
        BrowserUtils.openLinkExternal("http://www.rssowl.org/help"); //$NON-NLS-1$

      throw e;
    }
    fLinkHandler = new HashMap<String, ILinkHandler>();
    hookListeners();

    /* Add custom Context Menu on OS where this is not supported */
    if (Application.IS_LINUX || (useMozilla()))
      hookMenu();
  }

  private boolean useMozilla() {
    return Application.IS_WINDOWS && fgMozillaAvailable;
  }

  /**
   * Adds the given Handler to this instance responsible for the given Command.
   *
   * @param commandId The ID of the Command the provided Handler is responsible
   * for.
   * @param handler The Handler responsible for the fiven ID.
   */
  public void addLinkHandler(String commandId, ILinkHandler handler) {
    fLinkHandler.put(commandId, handler);
  }

  private Browser createBrowser(Composite parent, int style) {
    Browser browser = null;

    /* Properly configure Proxy for Firefox/XULRunner if required */
    if (Application.IS_LINUX || (useMozilla())) {
      String proxyHost = fEclipsePreferences.getString(DefaultPreferences.ECLIPSE_PROXY_HOST);
      String proxyPort = fEclipsePreferences.getString(DefaultPreferences.ECLIPSE_PROXY_PORT);
      if (useProxy() && StringUtils.isSet(proxyHost) && StringUtils.isSet(proxyPort)) {
        System.setProperty(XULRUNNER_PROXY_HOST, proxyHost);
        System.setProperty(XULRUNNER_PROXY_PORT, proxyPort);
      } else {
        System.setProperty(XULRUNNER_PROXY_HOST, ""); //$NON-NLS-1$
        System.setProperty(XULRUNNER_PROXY_PORT, ""); //$NON-NLS-1$
      }
    }

    /* Try Mozilla over IE on Windows */
    if (useMozilla()) {
      try {
        browser = new Browser(parent, style | SWT.MOZILLA);
        fgMozillaRunningOnWindows = true;
      } catch (SWTError e) {
        fgMozillaAvailable = false;

        if (!"No more handles [Could not detect registered XULRunner to use]".equals(e.getMessage())) //This happens too often to log it //$NON-NLS-1$
          Activator.getDefault().getLog().log(Activator.getDefault().createInfoStatus(e.getMessage(), null));
      }
    }

    /* Any other OS, or Mozilla unavailable, use default */
    if (browser == null)
      browser = new Browser(parent, style);

    /* Add Focusless Scroll Hook on Windows */
    if (Application.IS_WINDOWS)
      browser.setData(ApplicationWorkbenchWindowAdvisor.FOCUSLESS_SCROLL_HOOK, true);

    /* Disable IE Navigation Sound (Windows Only) */
    Method method = null;
    if (!fgNavigationSoundsDisabled) {
      method = callCoInternetSetFeatureEnabled(method, FEATURE_DISABLE_NAVIGATION_SOUNDS, SET_FEATURE_ON_PROCESS, true);
      fgNavigationSoundsDisabled = true;
    }

    /* Set Popupblocker if necessary */
    if (Application.IS_WINDOWS) {
      boolean prefEnablePopupBlocker = fPreferences.getBoolean(DefaultPreferences.ENABLE_IE_POPUP_BLOCKER);
      if (prefEnablePopupBlocker != fgPopupBlockerEnabled) {
        method = callCoInternetSetFeatureEnabled(method, FEATURE_WEBOC_POPUPMANAGEMENT, SET_FEATURE_ON_PROCESS, prefEnablePopupBlocker);
        callCoInternetSetFeatureEnabled(method, FEATURE_SECURITYBAND, SET_FEATURE_ON_PROCESS, prefEnablePopupBlocker);
        fgPopupBlockerEnabled = prefEnablePopupBlocker;
      }
    }

    /* Clear all Link Handlers upon disposal */
    browser.addDisposeListener(new DisposeListener() {
      public void widgetDisposed(DisposeEvent e) {
        fLinkHandler.clear();
      }
    });

    return browser;
  }

  private Method callCoInternetSetFeatureEnabled(Method method, int feature, int scope, boolean enable) {
    if (!fgCoInternetSetFeatureError && Application.IS_WINDOWS && !useMozilla()) {
      try {
        Class<?> clazz = Class.forName("org.eclipse.swt.internal.win32.OS"); //$NON-NLS-1$

        if (method == null)
          method = clazz.getMethod("CoInternetSetFeatureEnabled", int.class, int.class, boolean.class); //$NON-NLS-1$

        method.invoke(clazz, feature, scope, enable);

        return method;
      } catch (Throwable t) {
        Activator.getDefault().logError(t.getMessage(), t);
        fgCoInternetSetFeatureError = true;
      }
    }

    return null;
  }

  private boolean useProxy() {
    boolean useProxy = fEclipsePreferences.getBoolean(DefaultPreferences.ECLIPSE_USE_PROXY);
    boolean useSystemProxy = fEclipsePreferences.getBoolean(DefaultPreferences.ECLIPSE_USE_SYSTEM_PROXY);
    return useProxy && !useSystemProxy;
  }

  private void hookMenu() {
    MenuManager manager = new MenuManager();
    manager.setRemoveAllWhenShown(true);
    manager.addMenuListener(new IMenuListener() {
      public void menuAboutToShow(IMenuManager manager) {

        /* Back */
        manager.add(new Action(Messages.CBrowser_BACK) {
          @Override
          public void run() {
            fBrowser.back();
          }

          @Override
          public boolean isEnabled() {
            return fBrowser.isBackEnabled();
          }

          @Override
          public ImageDescriptor getImageDescriptor() {
            return OwlUI.getImageDescriptor("icons/etool16/backward.gif"); //$NON-NLS-1$
          }
        });

        /* Forward */
        manager.add(new Action(Messages.CBrowser_FORWARD) {
          @Override
          public void run() {
            fBrowser.forward();
          }

          @Override
          public boolean isEnabled() {
            return fBrowser.isForwardEnabled();
          }

          @Override
          public ImageDescriptor getImageDescriptor() {
            return OwlUI.getImageDescriptor("icons/etool16/forward.gif"); //$NON-NLS-1$
          }
        });

        /* Reload */
        manager.add(new Separator());
        manager.add(new Action(Messages.CBrowser_RELOAD) {
          @Override
          public void run() {
            refresh();
          }

          @Override
          public ImageDescriptor getImageDescriptor() {
            return OwlUI.getImageDescriptor("icons/elcl16/reload.gif"); //$NON-NLS-1$
          }
        });

        /* Stop */
        manager.add(new Action(Messages.CBrowser_STOP) {
          @Override
          public void run() {
            fBrowser.stop();
          }

          @Override
          public ImageDescriptor getImageDescriptor() {
            return OwlUI.getImageDescriptor("icons/etool16/cancel.gif"); //$NON-NLS-1$
          }
        });
      }
    });

    Menu menu = manager.createContextMenu(fBrowser);
    fBrowser.setMenu(menu);
  }

  /**
   * Returns the Browser-Widget this class is wrapping.
   *
   * @return The Browser-Widget this class is wrapping.
   */
  public Browser getControl() {
    return fBrowser;
  }

  /**
   * Refresh the Browser.
   */
  public void refresh() {
    fLastRefresh = System.currentTimeMillis();
    fBrowser.refresh();
  }

  /**
   * Browse to the given URL.
   *
   * @param url The URL to browse to.
   */
  public void setUrl(String url) {
    setUrl(url, false);
  }

  /**
   * Browse to the given URL.
   *
   * @param url The URL to browse to.
   * @param allowExternalNavigation <code>true</code> to allow external
   * navigation and <code>false</code> otherwise.
   */
  public void setUrl(String url, boolean allowExternalNavigation) {
    fLastSetUrl = url;

    /* Caller wants to allow external navigation - validate first */
    if (allowExternalNavigation) {

      /*
       * The only reason to allow external navigation is when the URL is
       * actually external (non local) and the user has choosen to open
       * links in the external Browser.
       */
      if (StringUtils.isSet(url) && !URIUtils.ABOUT_BLANK.equals(url) && !ApplicationServer.getDefault().isNewsServerUrl(url) && useExternalBrowser())
        fAllowExternalNavigation = true;
      else
        fLastUrlChange = System.currentTimeMillis();
    }

    /* Normal situation: External navigation blocked until page is loaded */
    else {
      fAllowExternalNavigation = false;
      fLastUrlChange = System.currentTimeMillis();
    }

    fBrowser.setUrl(url);
  }

  /**
   * Navigate to the previous session history item.
   *
   * @return <code>true</code> if the operation was successful and
   * <code>false</code> otherwise
   */
  public boolean back() {
    fAllowExternalNavigation = false;
    fLastUrlChange = System.currentTimeMillis();
    return fBrowser.back();
  }

  /**
   * Navigate to the next session history item.
   *
   * @return <code>true</code> if the operation was successful and
   * <code>false</code> otherwise
   */
  public boolean forward() {
    fAllowExternalNavigation = false;
    fLastUrlChange = System.currentTimeMillis();
    return fBrowser.forward();
  }

  /**
   * Print the Browser using the JavaScript print() method
   *
   * @return <code>TRUE</code> in case of success, <code>FALSE</code> otherwise
   */
  public boolean print() {
    setScriptDisabled(false);
    return fBrowser.execute(JAVA_SCRIPT_PRINT);
  }

  /* Special handling of opened websites on Windows (IE and Mozilla) */
  private OpenWindowListener getOpenWindowListener() {
    return new OpenWindowListener() {
      public void open(WindowEvent event) {

        /* Special handle external Browser */
        if (useExternalBrowser()) {

          /* Avoid IE being loaded from SWT on Windows */
          final Browser tempBrowser = new Browser(fBrowser.getShell(), useMozilla() ? SWT.MOZILLA : SWT.NONE);
          tempBrowser.setVisible(false);
          event.browser = tempBrowser;
          tempBrowser.getDisplay().timerExec(BROWSER_URL_DELAY, new Runnable() {
            public void run() {
              if (!tempBrowser.isDisposed() && PlatformUI.isWorkbenchRunning()) {
                String url = tempBrowser.getUrl();
                tempBrowser.dispose();
                if (StringUtils.isSet(url))
                  BrowserUtils.openLinkExternal(URIUtils.toUnManaged(url));
              }
            }
          });

          return;
        }

        /* Open internal Browser in a new Tab */
        if (fEclipsePreferences.getBoolean(DefaultPreferences.ECLIPSE_MULTIPLE_TABS)) {
          WebBrowserView browserView = BrowserUtils.openLinkInternal(null, WebBrowserContext.createFrom(Messages.CBrowser_LOADING));
          if (browserView != null)
            event.browser = browserView.getBrowser().getControl();
        }

        /* Open internal Browser in same Browser */
        else {
          final Browser tempBrowser = new Browser(fBrowser.getShell(), useMozilla() ? SWT.MOZILLA : SWT.NONE);
          tempBrowser.setVisible(false);
          event.browser = tempBrowser;
          tempBrowser.getDisplay().timerExec(BROWSER_URL_DELAY, new Runnable() {
            public void run() {
              if (!tempBrowser.isDisposed() && PlatformUI.isWorkbenchRunning()) {
                String url = tempBrowser.getUrl();
                tempBrowser.dispose();
                if (StringUtils.isSet(url))
                  setUrl(url);
              }
            }
          });
        }
      }
    };
  }

  /**
   * @return <code>true</code> if this browser is IE and <code>false</code>
   * otherwise.
   */
  public boolean isIE() {
    return Application.IS_WINDOWS && !fBrowser.isDisposed() && (fBrowser.getStyle() & SWT.MOZILLA) == 0;
  }

  /**
   * @return <code>true</code> if this browser is Mozilla on Windows and
   * <code>false</code> otherwise. Note that this will always return
   * <code>false</code> as long as a Browser has not been created.
   */
  public static boolean isMozillaRunningOnWindows() {
    return fgMozillaRunningOnWindows;
  }

  /**
   * @param disabled <code>true</code> to disable JavaScript and
   * <code>false</code> otherwise.
   */
  public void setScriptDisabled(Boolean disabled) {

    /* Only supported on Windows IE */
    if (!Application.IS_WINDOWS || (fBrowser.getStyle() & SWT.MOZILLA) != 0)
      return;

    /* Toggle via Reflection due to patched SWT */
    try {
      Method method = fBrowser.getClass().getMethod("setScriptDisabled", Boolean.class); //$NON-NLS-1$
      if (method != null)
        method.invoke(fBrowser, disabled);
    } catch (Exception e) {
      /* Ignore Silently */
    }
  }

  /**
   * @return <code>true</code> if the configuration is to disable JavaScript and
   * <code>false</code> otherwise.
   */
  public boolean shouldDisableScript() {
    return fPreferences.getBoolean(DefaultPreferences.DISABLE_JAVASCRIPT);
  }

  private boolean shouldDisableScript(String location) {
    if (!shouldDisableScript())
      return false;

    if (!StringUtils.isSet(location))
      return true;

    String[] websites = fPreferences.getStrings(DefaultPreferences.DISABLE_JAVASCRIPT_EXCEPTIONS);
    if (websites != null) {
      for (String website : websites) {
        if (location.contains(website))
          return false;
      }
    }

    return true;
  }

  private void hookListeners() {

    /* Listen to Open-Window-Changes */
    fBrowser.addOpenWindowListener(getOpenWindowListener());

    /* Listen to Location-Changes */
    fBrowser.addLocationListener(new LocationListener() {
      public void changed(LocationEvent event) {

        /* The website is fully loaded and external navigation is supported from now on. */
        if (event.top) {

          /*
          * Bug: It is possible that a changed() event is triggered for a previously visited
          * site although another site was already browsed to. In this case, we check if the
          * URL that was last set to the browser equals the event location to make sure the
          * changed() event is captured for the right moment.
          */
          if (StringUtils.isSet(event.location) && event.location.equals(fLastSetUrl))
            fAllowExternalNavigation = true;
        }
      }

      public void changing(LocationEvent event) {

        /* Update JavaScript enabled state */
        if (StringUtils.isSet(event.location) && !URIUtils.ABOUT_BLANK.equals(event.location) && !event.location.startsWith(URIUtils.JS_IDENTIFIER))
          setScriptDisabled(shouldDisableScript(event.location));

        /* Handle Application Protocol */
        if (event.location != null && event.location.contains(ILinkHandler.HANDLER_PROTOCOL)) {
          try {
            final URI link = new URI(event.location);
            final String host = link.getHost();
            if (StringUtils.isSet(host) && fLinkHandler.containsKey(host)) {
              try {
                fLinkHandler.get(host).handle(host, link);
              } finally {
                event.doit = false;
              }
              return;
            }
          } catch (URISyntaxException e) {
            Activator.getDefault().getLog().log(Activator.getDefault().createErrorStatus(e.getMessage(), e));
          }
        }

        /* Support opening Links in External Browser */
        if (useExternalBrowser()) {

          /* The URL must not be empty or about:blank (Problem on Linux) */
          if (!StringUtils.isSet(event.location) || URIUtils.ABOUT_BLANK.equals(event.location))
            return;

          /* Do not Let local ApplicationServer URLs open */
          if (ApplicationServer.getDefault().isNewsServerUrl(event.location))
            return;

          /* Find out if the Link is Managed (under our control) */
          boolean isManaged = URIUtils.isManaged(event.location);

          /* Only proceed if external navigation should not be blocked */
          if (!fAllowExternalNavigation) {
            if (!isManaged) //Workaround for Bug 1347: External Browser not used if page still loading
              return;
          }

          /* See Bug 1429: Potential external link opening from Browser.refresh() */
          long currentTimeMillis = System.currentTimeMillis();
          if (!isManaged && currentTimeMillis - fLastRefresh < REFRESH_NAVIGATION_DELAY)
            return;

          /* See Bug 1350: Potential browser popup when reading articles */
          if (Application.IS_MAC && !isManaged && currentTimeMillis - fLastUrlChange < URL_CHANGE_NAVIGATION_DELAY)
            return;

          /* Finally, cancel event and open URL external */
          event.doit = false;
          BrowserUtils.openLinkExternal(URIUtils.toUnManaged(event.location));
        }
      }
    });
  }

  private boolean useExternalBrowser() {
    return fPreferences.getBoolean(DefaultPreferences.USE_DEFAULT_EXTERNAL_BROWSER) || fPreferences.getBoolean(DefaultPreferences.USE_CUSTOM_EXTERNAL_BROWSER);
  }
}