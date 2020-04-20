# CM JSON

This module provides an easy to use Java API to read and write OSGi configurations in JSON format as defined by the [OSGi Configurator specification](https://osgi.org/specification/osgi.cmpn/7.0.0/service.configurator.html).

The primary purpose of this module is to be used by tooling dealing with OSGi configuration resources. The tools can directly leverage the read/write functionality without having to deal with the JSON format itself.

For example, the Apache Felix Configurator implementation uses this module for reading configuration resources.

