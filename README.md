# killbill-amberflo-catalog-plugin

Plugin to use [Amberflo](https://www.amberflo.io/) as a catalog.


## Kill Bill compatibility

| Plugin version | Kill Bill version  
| -------------: | -----------------:
| 1.x.y          | 0.24.z             



## Requirements

The plugin needs an amberflo API Key. See [https://www.amberflo.io/](https://www.amberflo.io/) for information on creating an account.

## Installation

Locally:

```
kpm install_java_plugin amberflo --from-source-file target/amberflo-catalog-*-.jar --destination /var/tmp/bundles
```

## Configuration

Go to [amberflo](https://www.amberflo.io/) and copy your `API key`.

Then, go to the Kaui plugin configuration page (`/admin_tenants/1?active_tab=PluginConfig`), and configure the `amberflo-catalog-plugin` plugin with your key:

```java
org.killbill.billing.plugin.amberflo.catalog.apiKey=test_XXX

```

Alternatively, you can upload the configuration directly:

```bash
curl -v \
     -X POST \
     -u admin:password \
     -H 'X-Killbill-ApiKey: bob' \
     -H 'X-Killbill-ApiSecret: lazar' \
     -H 'X-Killbill-CreatedBy: admin' \
     -H 'Content-Type: text/plain' \
     -d 'org.killbill.billing.plugin.amberflo.catalog.apiKey=test_XXX' \
     http://127.0.0.1:8080/1.0/kb/tenants/uploadPluginConfig/amberflo-catalog
```

## Catalog plugin flow

The plugin retrieves all of the necessary data from amberflo APIs and translates it into the form of a
Kill Bill Catalog. It retrieves all of the amberflo plans by making a call to the end-point specified
[here](https://docs.amberflo.io/reference/get_payments-pricing-amberflo-account-pricing-product-plans-list).

This plugin treats each element in the `feeMap` and in the `productItemPriceIdsMap` returned from the end point as a distinct plan. Plans from the `feeMap` and `productItemPriceIdsMap` are processed differently.

### Plans retrieved from `feeMap`

* The Kill Bill plan `name` is retrieved from the `id` value in the feeMap
* The Kill Bill plan `prettyName` is retrieved from the `name` value and adding it's billing period as a suffix
* The Kill Bill product `name` corresponds to the `id` in the amberflo feeMap
* The Kill Bill product `prettyName` corresponds to the `name` in the amberflo feeMap.
* The cost is retrieved from the `cost` value in the map

### Plans obtained from `ProductItemPriceIdsMap`

In this case, an HTTP request to the end-point specified [here] for each value in the map is made

* The Kill Bill plan `name` and product `name` are obtained from the `id` in the response
* The Kill Bill plan `prettyName` is retrieved from the `productItemName`, adding the billing period as suffix
* The Kill Bill product `prettyName` is retrieved from the `productItemName`
* The Kill Bill `unit` is obtained from the amberflo `meterApiName`

## Usage in the Kill Bill Catalog

The usage corresponds to the plans obtained from the `ProductItemPriceIdsMap` and is obtained by making an HTTP request to the end-point specified [here](https://docs.amberflo.io/reference/get_payments-pricing-amberflo-account-pricing-product-item-price)

* The Kill Bill `max` is retrieved from the `batchSize` in the response
* The cost is retrieved from the `pricePerBatch` value in the response
* The `size` is determined from the next tier on the list by applying the following operation (startAfterUnit (Final) – startAfterUnit(Initial)) / batchSize (Initial). If there is no following tier, the `size` will be `100,000` (a number that is big enough, so it will never be reached)

 
NOTES: 
* The plugin only considers plans with `lockingStatus: close_to_changes` in the response
* In this version, the plugin treats every usage as type `CONSUMABLE` and billing mode as `IN_ARREAR`
* All plans will be using USD as currency
* Plans from the fee map that are `One Time Fee` are considered as phase type `FIXED_TERM`,
all other plans are considered as phase type `EVERGREEN`

## Amberflo data supported by the plugin

Amberflo data that is being used as of this version:
###Plan data
* id
* productId
* billingPeriod (In this version, the plugin supports the following billing periods: daily, weekly, monthly and yearly.)
* productPlanName
* lastUpdatedTimeInMillis
* lockingStatus
* feeMap (id, name, cost, isOneTimeFee)
* productItemPriceIdsMap (id, productId, meterApiName, productItemName)

###Pricing data (tiers)
* startAfterUnit
* batchSize
* pricePerBatch

## Refreshing getLatestCatalogVersion

This plugin does a refresh of the latest catalog version via a call to `/plugins/amberflo-catalog/refresh`

```bash
curl -v \
     -X POST \
     -u admin:password \
     -H "X-Killbill-ApiKey: bob" \
     -H "X-Killbill-ApiSecret: lazar" \
     -H "Content-Type: application/json" \
     -H "Accept: application/json" \
     -H "X-Killbill-CreatedBy: demo" \
     -H "X-Killbill-Reason: demo" \
     -H "X-Killbill-Comment: demo" \
     "http://127.0.0.1:8080/plugins/amberflo-catalog/refresh"


