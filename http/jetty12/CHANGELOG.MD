# Changelog

All notable changes to the `org.apache.felix.http.jetty12` bundle are documented in this file.

## 2.0.1 (unreleased)

### Removed

- Configuration property `org.apache.felix.jetty.gzip.inflateBufferSize` removed. The new Jetty `CompressionHandler` API has no equivalent knob, so the property had no effect after the migration off the deprecated `GzipHandler`. Decompression behaviour now follows `CompressionHandler` defaults. ([FELIX-6832](https://issues.apache.org/jira/browse/FELIX-6832))

### Changed

- Migrated from the deprecated `org.eclipse.jetty.server.handler.gzip.GzipHandler` to `CompressionHandler` + `GzipCompression` + `CompressionConfig` (Jetty 12.1 compression API). All include/exclude lists (methods, paths, mime types), min compress size, and sync flush are preserved. ([FELIX-6832](https://issues.apache.org/jira/browse/FELIX-6832))
- Replaced deprecated `VirtualThreadPool.setMaxThreads` / `getMaxThreads` with `setMaxConcurrentTasks` / `getMaxConcurrentTasks`. ([FELIX-6832](https://issues.apache.org/jira/browse/FELIX-6832))

### Added

- New dependency on `org.eclipse.jetty.compression:jetty-compression-server`.
