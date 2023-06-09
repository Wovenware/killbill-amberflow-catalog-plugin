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
package org.killbill.billing.plugin.amberflo.catalog.core.resources;

import com.google.inject.Inject;
import javax.inject.Singleton;
import org.jooby.Result;
import org.jooby.Results;
import org.jooby.mvc.POST;
import org.jooby.mvc.Path;

@Singleton
@Path("/refresh")
public class AmberfloRefreshServlet {

  private final AmberfloRefreshService service;

  @Inject
  public AmberfloRefreshServlet(AmberfloRefreshService service) {
    this.service = service;
  }

  @POST
  public Result refreshGetLatestCatalogVersion() {
    service.refreshGetLatestCatalogVersion();

    return Results.ok("Catalog Refreshed");
  }
}
