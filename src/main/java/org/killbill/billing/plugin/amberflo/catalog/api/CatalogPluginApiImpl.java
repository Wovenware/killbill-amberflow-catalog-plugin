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
package org.killbill.billing.plugin.amberflo.catalog.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import org.joda.time.DateTime;
import org.killbill.billing.catalog.plugin.api.CatalogPluginApi;
import org.killbill.billing.catalog.plugin.api.StandalonePluginCatalog;
import org.killbill.billing.catalog.plugin.api.VersionedPluginCatalog;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.amberflo.catalog.api.boilerplate.VersionedPluginCatalogImp;
import org.killbill.billing.plugin.amberflo.catalog.client.AmberfloHttpClientImpl;
import org.killbill.billing.plugin.amberflo.catalog.core.CatalogConfigurationHandler;
import org.killbill.billing.plugin.amberflo.catalog.core.CatalogConfigurationProperties;
import org.killbill.billing.util.callcontext.TenantContext;

public class CatalogPluginApiImpl implements CatalogPluginApi {

  @Getter private final AtomicReference<DateTime> atomic;

  private final CatalogConfigurationHandler configHandler;

  public CatalogPluginApiImpl(final CatalogConfigurationHandler configHandler) {
    this.configHandler = configHandler;
    this.atomic = new AtomicReference<>(DateTime.now());
  }

  @Override
  public DateTime getLatestCatalogVersion(
      final Iterable<PluginProperty> properties, final TenantContext context) {
    return atomic.get();
  }

  public VersionedPluginCatalog getVersionedPluginCatalog(
      final Iterable<PluginProperty> properties, final TenantContext tenantContext) {

    final CatalogConfigurationProperties config =
        configHandler.getConfigurable(tenantContext.getTenantId());

    AmberfloHttpClientImpl client = new AmberfloHttpClientImpl(config);

    StandalonePluginCatalog standaloneCatalog = null;
    VersionedPluginCatalog versionedCatalog = null;
    try {
      standaloneCatalog = client.buildCatalog();

      versionedCatalog = standaloneToVersionedCatalog(standaloneCatalog);
    } catch (Exception e) {

      e.printStackTrace();
    }

    return versionedCatalog;
  }

  // Receives a StandalonePluginCatalog and converts it to a VersionedPluginCatalog.
  private VersionedPluginCatalog standaloneToVersionedCatalog(
      final StandalonePluginCatalog standaloneCatalog) {

    final List<StandalonePluginCatalog> list = new ArrayList<>();
    list.add(standaloneCatalog);
    final Iterable<StandalonePluginCatalog> versions = list;

    final VersionedPluginCatalogImp.Builder b =
        new VersionedPluginCatalogImp.Builder()
            .withStandalonePluginCatalogs(versions)
            .withCatalogName("Amberflo Catalog");

    return new VersionedPluginCatalogImp(b.build());
  }
}
