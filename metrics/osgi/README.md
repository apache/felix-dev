# Apache Felix OSGi Metrics

The OSGi metrics module defines a simple mechanism to gather OSGi-related metrics for application startup.

The module is split into two bundles:

- _collector_ - a zero-dependencies bundle that uses the OSGi APIs to gather various metrics
- _consumers_ - a single bundle that contains various consumers

## Metric collection

The metrics are collected by the `org.apache.felix.metrics.osgi.collector` bundle. This bundle requires no configuration and imports a minimal set of packages, to allow starting as early as possible.

As soon as startup is completed the metrics are made available to consumers that implement the `StartupMetricsListener` interface. The metrics are published after an optional delay, to prevent on-off bounces in startup completion.

Startup completion is delegated to either the `org.apache.felix.systemready` or the `org.apache.felix.healtchecks.api` bundles, which publish marker services once the system is considered ready.

## Metric publication

The `org.apache.felix.metrics.osgi.consumers` bundle contains three out-of-the-box implementation for publishing the metrics

- DropWizard metrics using a `MetricRegistry`
- JSON file written in the bundle data directory
- Log entries using the SLF4j API

### JSON metrics file sample

The following (truncated) JSON file exemplifies how the metrics are written

```json
{
  "application": {
    "startTimeMillis": 1587469534671,
    "startDurationMillis": 14635
  },
  "bundles": [
    {
      "symbolicName": "org.osgi.util.pushstream",
      "startTimeMillis": 1587469535933,
      "startDurationMillis": 0
    },
    {
      "symbolicName": "org.apache.aries.util",
      "startTimeMillis": 1587469535935,
      "startDurationMillis": 0
    },
    {
      "symbolicName": "org.apache.felix.configadmin",
      "startTimeMillis": 1587469536313,
      "startDurationMillis": 58
    }
  ],
  "services": [
    {
      "identifier": "jmx.objectname=org.apache.sling.classloader:name=FSClassLoader,type=ClassLoader",
      "restarts": 2
    }
  ]
}
```

Similar metrics are reported through the other collectors.

## Usage

1. Add the `org.apache.felix/org.apache.felix.metrics.osgi.collector` bundle and ensure that
   it starts as early as possible
1. Add the `org.apache.felix/org.apache.felix.metrics.osgi.consumers` bundle.
1. Add the required bundles, either Apache Felix SystemReady or Apache Felix Health Checks
1. Start up the application
