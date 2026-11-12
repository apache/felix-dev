<!--
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
-->

# Plurl (vendored)

## Provenance

These sources are copied from the Eclipse OSGi Technology **plurl** project:

- Upstream: https://github.com/eclipse-osgi-technology/plurl
- Originally: https://github.com/tjwatson/plurl-osgi

The **only** modification is the package rename from `org.eclipse.osgitech.plurl` to
`org.apache.felix.framework.plurl`. Every file keeps its original license header and
copyright notice unchanged.

This mirrors what Eclipse Equinox did in
https://github.com/eclipse-equinox/equinox/pull/848, which vendored the same files
into `org.eclipse.equinox.plurl`.

## Licensing

The sources are **Apache-2.0**:

```
Copyright (c) Contributors to the Eclipse Foundation
Licensed under the Apache License, Version 2.0
SPDX-License-Identifier: Apache-2.0
```

An earlier copy carried EPL-2.0 headers, which would have been a blocker: EPL-2.0 is
[Category B](https://www.apache.org/legal/resolved.html#category-b) at the ASF and may
not be included in an Apache source release. That was an oversight when the code moved
to the osgi-technology project and has been corrected upstream in
https://github.com/eclipse-osgi-technology/plurl/pull/45. The copy here also includes
the upstream fix from https://github.com/eclipse-osgi-technology/plurl/pull/55.

## Vendoring is intended to be temporary

The longer term intention, per
https://github.com/apache/felix-dev/pull/552#issuecomment-5466491363, is a release of
plurl from the osgi-technology project consumed by both Equinox and Felix
**unchanged, in its original package**, rather than copied into each framework. At the
time of writing plurl has no release: its `distributionManagement` points at
`oss.sonatype.org`, which was decommissioned when OSSRH migrated to the Central
portal, so neither a release nor a snapshot is currently resolvable.

Once a release exists, this directory should be deleted and replaced by a dependency
on the published artifact, embedded into the framework bundle as a private package.

## Why the framework uses this

`URLHandlers` used to claim the JVM-wide `java.net.URL` stream handler factory by
reflectively swapping a private static field, and inspected the call stack to work out
which framework instance a call belonged to. Obtaining a `MethodHandles.Lookup`
trusted enough for that swap is the only remaining reason the framework uses
`sun.misc.Unsafe`, and whichever framework installed itself last won the singleton.

Plurl installs one cooperative router through the supported
`URL.setURLStreamHandlerFactory` API and routes by asking each registered factory
whether a calling class belongs to it, so several frameworks can coexist in one JVM.
See `PlurlURLHandlers` for the Felix side of that.
