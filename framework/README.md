# Apache Felix Framework - 7.x branch

This is the `framework-7.x` branch: the maintenance line for the framework's 7.x
releases. It exists for users who cannot move to 8.x, and it is where fixes for
them are made.

For documentation on how to use, launch, and/or embed the framework, see
[`doc/README.txt`](doc/README.txt). A full change history is in
[`doc/changelog.txt`](doc/changelog.txt).

## Why this branch exists

Framework 8.0.0 removed the OSGi security layer and raised the minimum Java version
to 11, because Java SE 24 permanently disabled the Security Manager
([JEP 486](https://openjdk.org/jeps/486)). `master` is now the 8.x line.

Everything 7.x users depend on is still here: this branch starts at `a6b84a9596`, the
last commit before those changes landed, which is also tagged `framework-7.x-last`.

| | 7.x (this branch) | 8.x (`master`) |
|---|---|---|
| Minimum Java version | 8 | 11 |
| Runs on Java 24 and later | no | yes |
| OSGi security layer | supported (`framework.security`) | **removed** |
| `Require-Capability: osgi.ee` | `JavaSE 1.8` | `JavaSE 11` |

Use **7.x** if you need the security layer, or must run on Java 8. Use **8.x** if you
need to run on Java 24 or later.

The latest 7.x release is 7.0.5. The version in `pom.xml` is `7.1.0-SNAPSHOT`.

## What belongs here

- Bug fixes and security fixes for 7.x users.
- Not new features: those go to `master`.

Make a fix on `master` first and backport it here with a pull request against
`framework-7.x`, so that it is not lost from 8.x. Much of the framework was changed
when the security layer was removed (`SecureAction`, `BundleImpl`, `Felix`,
`EventDispatcher` among others), so a fix touching those files will not cherry-pick
cleanly and needs porting by hand. Fixes in code that 8.x left alone usually apply
as they are.

## Building and CI

CI runs on JDK 17 and 21. JDK 25 is deliberately not in the matrix: 7.x does not run
on Java 24 and later, where the Security Manager is permanently disabled.
