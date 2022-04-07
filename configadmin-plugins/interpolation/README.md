# org.apache.felix.configadmin.plugin.interpolation

An OSGi Configuration Admin Plugin that can interpolate values in configuration with values obtained elsewhere.

The [StandaloneInterpolator](./src/main/java/org/apache/felix/configadmin/plugin/interpolation/StandaloneInterpolator.java)
can also be used independently of Configuration Admin.

Supported sources:

* Files on disk, for example to be used with Kubernetes secrets
* Environment variables
* Framework properties
* System properties
* Other properties within the same configuration (self references)

## Interpolating Secret Files

Usually secrets (for example when provided by Kubernetes) will surface as files at a certain mount point, e.g.:

```
$ ls /mnt/mysecrets
db.password
my.api.key
another.secret
```

The file contents are opaque and contain the secret value as-is.

This plugin will replace OSGi Configuration Admin values declared
using secrets placeholders. These placeholders start with `$[secret:`
and end with `]`. The word inside the placeholder identifies the secret
name as found in the secrets directory on the filesystem. For example:

```
com.my.database:
"user": "my-user",
"password": "$[secret:db.password]"
```

In this example the `user` name for the database is left as-is but for the
`password` the content of the `/mnt/mysecrets/db.password` file is used.

## Interpolating environment variables

Environment variables can be substituted in configuration values by using the
`$[env:SOME_VAR]` syntax. This will use the value of the `SOME_VAR` environment variable.

The placeholder can be part of a larger configuration string, for example:

```
com.my.userinfo:
"greeting": "Hello $[env:USER]!"
```

## Interpolating Framework/System properties

Properties can also be interpolated in the configuration. The properties values are
obtained through the `BundleContext.getProperty(key)` API, which will return the framework
property for the key. If the framework property is not specified, the system property
with this key is returned.

Property values are obtained through the `$[prop:my.property]` syntax.

## Interpolating configuration properties (self references)

Interpolated values can also come from other values in the configuration itself.

Configuration values are obtained through the `$[conf:my.property]` syntax.

*Note:* Cycles are prevented and logged.

## Default Values

It is possible to specify a default value as part of the placeholder, for example:

```
"port" : "$[env:PORT;default=8080]"
"label" : "$[env:LABEL;default=]"
```

Without a default, the placeholder is left in the value if no value can be found. With a default, the default is used instead.

The default value may contain all alphanumeric and punctuation characters and space except for a `;`. It may even be left out which leads to replacing the placeholder with the empty string.

## Type Support

A placeholder can contain additional information like the type the value should be converted to.


```
"port" : "$[env:PORT;type=Integer]"
```

In the above example, an Integer object with the port will be put into the Configuration instead of a String value.

### Supported Scalar and Primitive Types

The following types are supported: String, Integer, int, Long, long, Float, float, Double, double, Byte, byte, Short, short, Character, char, Boolean, boolean.

### Supported Array Types

The following array types are supported: String[], Integer[], int[], Long[], long[], Float[], float[], Double[], double[], Byte[], byte[], Short[], short[], Character[], char[], Boolean[], boolean[].

A provided value (including the default value) can be split up into a string array before conversion by configuring a delimiter as part of the placeholder:

```
"ports" : "$[env:PORT;type=Integer[];delimiter=,;default=8080,8081]"
```

## Configuration of the plugin

The plugin (and Configuration Admin) can be controlled by various properties. These properties are
framework properties and can be provided on initialization of the OSGi framework or as system properties
as framework properties default to system properties.

### Consistent processing

It is recommended to configure the Configuration Admin to only start processing once this plugin is active. Otherwise there is the risk that configurations get delivered without placeholders being replaced.

In case of the Apache Felix ConfigAdmin implementation, this can be achieved by using the following property:

* `felix.cm.config.plugins`: `org.apache.felix.configadmin.plugin.interpolation`

### Secrets lookup

In order to look up secrets on the filesystem, the plugin must be provided with the directories
where these can be found.

This is done through the following property:

* `org.apache.felix.configadmin.plugin.interpolation.secretsdir`: specify a comma-separated (`,`) list of directories where the files used for the file-based interpolation, such as Kubernetes secrets, are mounted.

If the property is not present, the plugin will function, but without being able to replace values based on secrets.

### File Encoding

When reading files, for example secrets, the platform default encoding is used. The following property can be used to to control the reading:

* `org.apache.felix.configadmin.plugin.interpolation.file.encoding` : specify the encoding to be used.

## Consuming Configurations with Placeholders

If you are getting a [Configuration](https://docs.osgi.org/javadoc/osgi.cmpn/7.0.0/org/osgi/service/cm/Configuration.html) object directly from [ConfigurationAdmin](https://docs.osgi.org/javadoc/osgi.cmpn/7.0.0/org/osgi/service/cm/ConfigurationAdmin.html) and inspect the properties, you will find the placeholders not being replaced in those values as configuration plugins are not invoked. So for example calling [`getConfiguration`](https://docs.osgi.org/javadoc/osgi.cmpn/7.0.0/org/osgi/service/cm/ConfigurationAdmin.html#getConfiguration-java.lang.String-) or []`listConfigurations`](https://docs.osgi.org/javadoc/osgi.cmpn/7.0.0/org/osgi/service/cm/ConfigurationAdmin.html#listConfigurations-java.lang.String-) and then calling [`getProperties`](https://docs.osgi.org/javadoc/osgi.cmpn/7.0.0/org/osgi/service/cm/Configuration.html#getProperties--) on the returned Configuration object will return the placeholders - not the replaced values.

There are different options on how to get the values replaced:
* Register a [`ManagedService`](https://docs.osgi.org/javadoc/osgi.cmpn/7.0.0/org/osgi/service/cm/ManagedService.html) or [`ManagedServiceFactory`](https://docs.osgi.org/javadoc/osgi.cmpn/7.0.0/org/osgi/service/cm/ManagedServiceFactory.html). ConfigurationAdmin calls all plugins before delivering configurations to these services.
* Use [`Declarative Services`](https://docs.osgi.org/specification/osgi.cmpn/7.0.0/service.component.html) - when configuration values are delivered to your component, plugins are called as well.
* Call [getProcessedProperties](https://docs.osgi.org/javadoc/osgi.cmpn/7.0.0/org/osgi/service/cm/Configuration.html#getProcessedProperties-org.osgi.framework.ServiceReference-) on the Configuration object - instead of just `getProperties`. For this you need a valid service reference.
