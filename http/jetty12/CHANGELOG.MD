# Changelog

All notable changes to the `org.apache.felix.http.jetty12` bundle are documented in this file.

## 2.0.1 (unreleased)

### Removed

- Configuration property `org.apache.felix.jetty.gzip.inflateBufferSize` removed. The new Jetty `CompressionHandler` API has no equivalent knob, so the property had no effect after the migration off the deprecated `GzipHandler`. Decompression behaviour now follows `CompressionHandler` defaults. ([FELIX-6832](https://issues.apache.org/jira/browse/FELIX-6832))

### Changed

- Updated embedded Jetty to **12.1.10** (was 12.1.9). ([FELIX-6834](https://issues.apache.org/jira/browse/FELIX-6834))
- Migrated from the deprecated `org.eclipse.jetty.server.handler.gzip.GzipHandler` to `CompressionHandler` + `GzipCompression` + `CompressionConfig` (Jetty 12.1 compression API). All include/exclude lists (methods, paths, mime types), min compress size, and sync flush are preserved. ([FELIX-6832](https://issues.apache.org/jira/browse/FELIX-6832))
- Replaced deprecated `VirtualThreadPool.setMaxThreads` / `getMaxThreads` with `setMaxConcurrentTasks` / `getMaxConcurrentTasks`. ([FELIX-6832](https://issues.apache.org/jira/browse/FELIX-6832))

### Added

- New dependency on `org.eclipse.jetty.compression:jetty-compression-server`.

## 2.0.0

### Changed

- Updated embedded Jetty to **12.1.9** (was 12.0.x). ([FELIX-6750](https://issues.apache.org/jira/browse/FELIX-6750))
- Migrated from Jakarta EE10 to **Jakarta EE11**: the bundle now imports the `org.eclipse.jetty.ee11.servlet.*` packages and depends on `jetty-ee11-servlet` (was `jetty-ee10-servlet`). Jakarta Servlet API moves to **6.1**. ([FELIX-6750](https://issues.apache.org/jira/browse/FELIX-6750))
  - An EE10 variant on Jetty 12.1.9 is maintained on the [`http.jetty12.ee10`](https://github.com/apache/felix-dev/tree/http.jetty12.ee10) branch for consumers that cannot yet move to EE11.
- Updated `maven-bundle-plugin` to 6.0.2.

### Added

- New dependencies on `org.eclipse.jetty.compression:jetty-compression-gzip` and `org.eclipse.jetty.compression:jetty-compression-common`, reflecting the Jetty 12.1 split of compression into separate modules.
