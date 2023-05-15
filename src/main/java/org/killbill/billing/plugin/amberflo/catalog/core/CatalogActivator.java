/* Copyright 2023 Wovenware, Inc
 *
 * Wovenware licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.plugin.amberflo.catalog.core;

import java.util.Hashtable;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import org.killbill.billing.catalog.plugin.api.CatalogPluginApi;
import org.killbill.billing.osgi.api.Healthcheck;
import org.killbill.billing.osgi.api.OSGIPluginProperties;
import org.killbill.billing.osgi.libs.killbill.KillbillActivatorBase;
import org.killbill.billing.plugin.amberflo.catalog.api.CatalogPluginApiImpl;
import org.killbill.billing.plugin.amberflo.catalog.core.resources.AmberfloRefreshService;
import org.killbill.billing.plugin.amberflo.catalog.core.resources.AmberfloRefreshServlet;
import org.killbill.billing.plugin.amberflo.catalog.core.resources.CatalogTestHealthcheckServlet;
import org.killbill.billing.plugin.api.notification.PluginConfigurationEventHandler;
import org.killbill.billing.plugin.core.config.PluginEnvironmentConfig;
import org.killbill.billing.plugin.core.resources.jooby.PluginApp;
import org.killbill.billing.plugin.core.resources.jooby.PluginAppBuilder;
import org.osgi.framework.BundleContext;

public class CatalogActivator extends KillbillActivatorBase {

  public static final String PLUGIN_NAME = "amberflo-catalog";

  private CatalogConfigurationHandler configurationHandler;

  @Override
  public void start(final BundleContext context) throws Exception {
    super.start(context);

    final String region = PluginEnvironmentConfig.getRegion(configProperties.getProperties());
    configurationHandler = new CatalogConfigurationHandler(region, PLUGIN_NAME, killbillAPI);

    final CatalogPluginApiImpl catalogPluginApi = new CatalogPluginApiImpl(configurationHandler);
    registerCatalogPluginApi(context, catalogPluginApi);

    // Expose a healthcheck (optional), so other plugins can check on the plugin status
    final Healthcheck healthcheck = new CatalogTestHealthcheck();
    registerHealthcheck(context, healthcheck);

    final AmberfloRefreshService refreshService = new AmberfloRefreshService(catalogPluginApi);

    // Register a servlet (optional)
    final PluginApp pluginApp =
        new PluginAppBuilder(PLUGIN_NAME, killbillAPI, dataSource, super.clock, configProperties)
            .withRouteClass(CatalogTestHealthcheckServlet.class)
            .withRouteClass(AmberfloRefreshServlet.class)
            .withService(healthcheck)
            .withService(refreshService)
            .build();

    final HttpServlet httpServlet = PluginApp.createServlet(pluginApp);
    registerServlet(context, httpServlet);

    registerEventHandlers();
  }

  private void registerEventHandlers() {
    final PluginConfigurationEventHandler configHandler =
        new PluginConfigurationEventHandler(configurationHandler);
    dispatcher.registerEventHandlers(configHandler);
  }

  private void registerCatalogPluginApi(final BundleContext context, final CatalogPluginApi api) {
    final Hashtable<String, String> props = new Hashtable<>();
    props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
    registrar.registerService(context, CatalogPluginApi.class, api, props);
  }

  private void registerServlet(final BundleContext context, final Servlet servlet) {
    final Hashtable<String, String> props = new Hashtable<>();
    props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
    registrar.registerService(context, Servlet.class, servlet, props);
  }

  private void registerHealthcheck(final BundleContext context, final Healthcheck healthcheck) {
    final Hashtable<String, String> props = new Hashtable<>();
    props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
    registrar.registerService(context, Healthcheck.class, healthcheck, props);
  }
}
