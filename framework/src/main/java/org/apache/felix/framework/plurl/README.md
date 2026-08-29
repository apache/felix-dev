# Plurl (vendored) — PROTOTYPE, NOT FOR RELEASE

## Provenance

These sources are copied verbatim from the Eclipse OSGi Technology **plurl** project:

- Upstream: https://github.com/eclipse-osgi-technology/plurl
- Originally: https://github.com/tjwatson/plurl-osgi
- Copied at commit `6581777`, upstream version `0.1.0-SNAPSHOT`

The **only** modification is the package rename from `org.eclipse.osgitech.plurl` to
`org.apache.felix.framework.plurl`. Every file keeps its original license header and
its `Copyright (c) 2025 IBM Corporation` notice unchanged.

This mirrors what Eclipse Equinox did in
https://github.com/eclipse-equinox/equinox/pull/848, which vendored the same 11 files
into `org.eclipse.equinox.plurl`.

## ⚠️ Unresolved licensing issue

**This code must not be merged or released in its current state.**

Every source file here declares:

```
SPDX-License-Identifier: EPL-2.0
Copyright (c) 2025 IBM Corporation
```

EPL-2.0 is [Category B](https://www.apache.org/legal/resolved.html#category-b) at the
ASF and **may not be included in an Apache source release**. Equinox was free to
vendor these files because Eclipse projects are EPL-2.0 natively; Apache Felix is not.

There is reason to believe the headers are an oversight rather than the project's
intent: the plurl repository's own `LICENSE` file and its `pom.xml` both declare
**Apache-2.0**, and only the source headers say EPL-2.0. (Its `NOTICE` file is also a
copy-paste leftover referring to "slf4j-osgi".)

Before this can go anywhere, one of the following has to happen:

1. The upstream project relicenses/corrects the source headers to Apache-2.0, so the
   files can legitimately live in an Apache source tree; or
2. Felix consumes plurl as a released binary dependency rather than vendored source,
   subject to the Category B rules — which additionally requires plurl to be published
   to Maven Central, as it currently has no release or tag; or
3. Felix writes its own Apache-2.0 implementation of the same idea.

## Why we want this at all

`URLHandlers` currently takes over the JVM-wide `java.net.URL` stream handler factory
by reflectively swapping a private static field, which is what forces
`SecureAction`'s use of `sun.misc.Unsafe` to obtain a trusted
`MethodHandles.Lookup`. That is the only remaining `Unsafe` usage in the framework and
it prevents Felix and Equinox from coexisting in one JVM without clobbering each
other's URL singletons. Plurl replaces that with a cooperative multiplexing factory,
which is the approach suggested in
https://github.com/apache/felix-dev/pull/433#issuecomment-3073468820.
