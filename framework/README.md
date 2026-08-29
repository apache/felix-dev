# Apache Felix Framework

An implementation of the [OSGi Core](https://docs.osgi.org/specification/osgi.core/8.0.0/)
specification.

For documentation on how to use, launch, and/or embed the framework, see
[`doc/README.txt`](doc/README.txt). A full change history is in
[`doc/changelog.txt`](doc/changelog.txt).

## Choosing a version

The framework is maintained in two lines. They differ in the Java versions they
support and in whether the OSGi security layer is available.

| | 7.x | 8.x |
|---|---|---|
| Minimum Java version | 8 | 9 |
| Runs on Java 24 and later | no | yes |
| OSGi security layer | supported | **removed** |
| `Require-Capability: osgi.ee` | `JavaSE 1.8` | `JavaSE 9` |

Pick **7.x** if you need the security layer, or if you must run on Java 8.
Pick **8.x** if you need to run on Java 24 or later.

## What changed in 8.0.0

### The OSGi security layer has been removed

This is the reason for the major version bump, and the only change that requires
action when upgrading.

Java SE 24 permanently disabled the Security Manager
([JEP 486](https://openjdk.org/jeps/486)). `System.setSecurityManager` throws
`UnsupportedOperationException`, so the framework can no longer install one and no
permission check can ever run. Rather than appear to enforce permissions while
silently enforcing nothing, the framework no longer implements the security layer at
all.

What this means in practice:

- **Launching with `org.osgi.framework.security` set now fails** with a
  `SecurityException`. Previously the framework installed a Security Manager. It does
  not fail silently, so a launcher that depends on security will not start rather than
  start unprotected.
- **`Bundle.hasPermission(Object)` returns `true`** unless a `SecurityProvider` has
  been installed explicitly.
- **No permission checks are performed** on bundle lifecycle operations, service
  registration and lookup, resource and class loading, weaving, or resolution.
- **The `org.apache.felix.framework.security` bundle has been removed.** It existed
  only to supply the `PermissionAdmin` and `ConditionalPermissionAdmin` services to
  this framework.

The OSGi permission API is **still exported and unchanged**: `AdminPermission`,
`ServicePermission`, `PackagePermission`, `BundlePermission`, `CapabilityPermission`,
`AdaptPermission`, `Bundle.hasPermission`, `ProtectionDomain` and
`org.apache.felix.framework.ext.SecurityProvider` all remain. Bundles that reference
those types continue to compile and resolve; the types simply no longer gate
anything.

Bundles that merely *declare* permissions, for example by shipping
`OSGI-INF/permissions.perm`, need no change. Only code that *relies on a permission
being denied* is affected.

### Minimum Java version raised to 9

The framework inspects the caller's class context in a few places to work out which
framework instance or bundle a call belongs to. That used to be done with a
`SecurityManager` subclass, purely to reach the protected `getClassContext()` method.
It now uses `java.lang.StackWalker`, which is the supported replacement and available
from Java 9.

The bundle therefore declares `Require-Capability: osgi.ee ... JavaSE 9` and will not
resolve on a Java 8 VM. Use the 7.x line if you need Java 8.

### Other changes

- `sun.misc.Unsafe` is still used in one place, to obtain a trusted
  `MethodHandles.Lookup` for taking over the JVM-wide `java.net.URL` stream handler
  factory. It is guarded and falls back to `AccessibleObject.setAccessible`, so it
  degrades to a warning on recent JDKs. Replacing it is tracked separately.
- Verified on Java 17, 21, 23 and 25, including the OSGi Core R8 TCK.
