# Apache Felix Web Console

The Apache Felix Web Console is a powerful tool to inspect and manage OSGi framework instances using your favourite Web Browser. The console is extensible via plugins.

## Requirements

As the Web Console is a web application, it requires some APIs and services to be availble.

The required dependencies are:

- OSGi Framework R7 : For example the [Apache Felix Framework](https://felix.apache.org/documentation/subprojects/apache-felix-framework.html)
- OSGi Http Servlet WHiteboard : This includes support for the Jakarta Servlet API 5. For exmaple the [Apache Felix Http Implementation](https://github.com/apache/felix-dev/tree/master/http)
- SLF4j Logging API 1.7 (or higher)
- OWASP Encoder 1.2 (or higher)
- [Apache Felix Inventory](https://felix.apache.org/documentation/subprojects/apache-felix-inventory.html) 2.0.0 (or higher)

## Installation

The installation of the web console is straight forward. Provide an OSGi Framework with the mentioned required dependenices and install the bundle.

**Important:** The webconsole does not provide a CSRF protection out of the box! Therefore it is advisable to install additional software to protect the webconsole. For example the bundle from [Apache Sling Security](https://github.com/apache/sling-org-apache-sling-security) provides this additional protection. Make sure to use the latest version (1.3.0 or higher).


## Configuration

The Web Console can be configured via framework properties as well as via a configuration through the OSGi Configuration Admin Service. The framework properties can be used in case your runtime does not provide a OSGi Configuration Admin Service.

### OSGi Configuration Admin

The Web Console can be configurated through a configuration using the service PID `org.apache.felix.webconsole.internal.servlet.OsgiManager`. This configuration supports the following properties:

| Property | Default Value | Description |
|---|---|---|
| `manager.root`| `/system/console`| The root path to the OSGi Management Console. |
| `default.render` | `bundles`| The name of the default configuration page  when invoking the OSGi Management console. |
| `realm` | `OSGi Management Console` | The name of the HTTP Authentication Realm. |
| `username` | `admin` | The name of the user allowed to access the OSGi Management Console. To disable authentication clear this value. |
| `password` | `admin` | The password for the user allowed to access the OSGi Management Console. |
| `plugins` | all plugins enabled | The labels of the plugins enabled and displayed. |
| `locale` | -- | If set, this locale forces the localization to use this locale instead of the one requested by the web browser. |
| `http.service.filter` | -- | OSGi filter used to select the Http Service to which the Web Console binds. The value of this property (if not empty) is combined with the object class selection term to get the actual service selection filter like `(&(objectClass=org.osgi.service.http.HttpService)(filter))`. This property must not have leading or ending parentheses. For example, to bind to the service with service ID 15 set the property to `service.id=15`. By default (if this property is not set or set to an empty string) the Web Console binds with any Http Service available. |
| `shutdown.timeout` | 5 | The timeout for felix shutdown |
| `reload.timeout` | 40 | The timeout for webconsole page reload, after shutdown |

### Framework Properties

Some of the configuration properties supported through the OSGi Configuration Admin service can also be set globally and statically as framework properties.
Such framework properties will also be considered actual default values for missing properties in Configuration Admin configuration as well as for the Metatype descriptor.

| Framework Property | Configuration Admin Property |
|---|---|
| `felix.webconsole.manager.root` | `manager.root` |
| `felix.webconsole.realm` | `realm` |
| `felix.webconsole.username` | `username` |
| `felix.webconsole.password` | `password` |
| `felix.webconsole.locale` | `locale` |

Please note that setting any of these properties as framework property makes them visible to all bundles deployed. This is particularly to be considered in case of the `felix.webconsole.password` property (as for authentication, the use of a [Web Console Security Provider](EXTENSIONS.md) is suggested anyway).

## Security

**Important:** The webconsole does not provide a CSRF protection out of the box! Therefore it is advisable to install additional software to protect the webconsole. For example the bundle from [Apache Sling Security](https://github.com/apache/sling-org-apache-sling-security) provides this additional protection. Make sure to use the latest version (1.3.0 or higher).

The Web Console only has very basic security at the moment supporting only HTTP Basic authentication. This security is enabled by default and may be disabled by simply clearing the `username` property.

To enhance the security of the Web Console you are strongly encouraged to change at least the `password` for the admin user.

This simple user setup can and should be extended by providing a [Web Console Security Provider](EXTENSIONS.md). See that page for more information.

## Extending the Web Console

The Web Console can be extended by registering an OSGi service for the interface `jakarta.servlet.Servlet`.

Please refer to [Extending the Apache Felix Web Console](EXTENSIONS.md) for full documentation on extending the Web Console.

## RESTful API

While the Web Console does not have a full featured and documented REST-ful API, most plugins try to follow REST approaches. For example the bundles plugin is able to send information on all bundles or a single bundle.

An attempt is made to document the current state of REST-like APIs at link [Web Console RESTful API](RESTAPI.md).

## Issues

Should you have any questions using the Web Console, please send a note to one of our [Mailing Lists](https://felix.apache.org/documentation/community/project-info.html#_mailing_lists).

Please report any issues with the Web Console in our [issue tracking system](https://issues.apache.org/jira/browse/Felix) and be sure to report for the _Web Console_ component. See our [Issue Tracking](https://felix.apache.org/documentation/community/project-info.html#_issue_tracking) page for more details.

## Plugins

The web console includes some plugins which will be enabled if additional dependencies are satisfied:

- OSGi Log Service : A plugin to show the logging output

Additional plugins can be found in the [plugins directory](https://github.com/apache/felix-dev/tree/master/webconsole-plugins).

### OSGi Configuration Admin Service

If an OSGi Configuration Admin Service is available at runtime, the Configuration Manager plugin can be used to manage OSGi configurations. For human readbable configuration descriptions it is advisable to also install an OSGi Metatype service.

The Configuration Manager is available via `http://localhost:8888/system/console/configMgr`. It display all configurable OSGi services.

#### Configuration Factories

The Configuration Manager has special support for configuration factories by allowing to add new items via the "plus" buttons or editing or removing existing ones.

By default for each confguration factory item a unique ID is displayed, which is quite cryptic, for example: `org.apache.felix.jaas.Configuration.factory.18a6be2a-3173-4120-8f56-77fabff7b7ea`. The developer of the service using a configuration factory can define a special `name hint` configuration propery which defines a name template that is used to build the configuration factory item name when displayed in the Configuration Manager. The name of this property is `webconsole.configurationFactory.nameHint`. It allows referencing other service property names as placeholders by enclosing in brackets.

Example:

```
webconsole.configurationFactory.nameHint = "{jaas.realmName}, {jaas.classname}"
jaas.realmName = "myRealm"
jaas.classname = "myClass"
```

In this case the Configuration Manager displays the name "myRealm, myClass" as display name for the configuration entry which is much more human-readable than the cryptic name. The Configuration Manager will not display the property `webconsole.configurationFactory.nameHint` as a configuration property.


## Releases

### Changes in 5.0.10 (5/Feb/25)

#### Bug

- [FELIX-6747](https://issues.apache.org/jira/browse/FELIX-6747) : NPE in activator of webconsole
- [FELIX-6751](https://issues.apache.org/jira/browse/FELIX-6751) : Use proper encoding for service filter


### Changes in 5.0.8 (19/Sep/24)

#### Bug

- [FELIX-6727](https://issues.apache.org/jira/browse/FELIX-6727) : NullPointerException when using REST API to install a bundle


### Changes in 5.0.6 (10/Jul/24)

#### Bug

- [FELIX-6715](https://issues.apache.org/jira/browse/FELIX-6715) : Incorrect link in "Using bundles" for /system/console/services/<serviceid>


### Changes in 5.0.4 (20/Jun/24)

#### Improvements

- [FELIX-6712](https://issues.apache.org/jira/browse/FELIX-6712) : Release Websonsole - without JSON License
#### Docs

- [FELIX-6703]()https://issues.apache.org/jira/browse/FELIX-6703 : Missing conf properties in the documentation

### Changes in 5.0.2 (14/Apr/24)

#### Bug

- [FELIX-6688](https://issues.apache.org/jira/browse/FELIX-6688) : Context path of outer servlet container is not respected for authentication


### Changes in 5.0.0 (8/Jan/24)

#### New Feature

- [FELIX-6638](https://issues.apache.org/jira/browse/FELIX-6638) : Migrate WebConsole to Jakarta Servlet API
- [FELIX-6644](https://issues.apache.org/jira/browse/FELIX-6644) : Switch to Java 11 as base java version
- [FELIX-6651](https://issues.apache.org/jira/browse/FELIX-6651) : Use slf4j for logging

#### Improvement

- [FELIX-6652](https://issues.apache.org/jira/browse/FELIX-6652) : Remove deprecated rendering attributes
- [FELIX-6653](https://issues.apache.org/jira/browse/FELIX-6653) : Remove all and debug bundling
- [FELIX-6654](https://issues.apache.org/jira/browse/FELIX-6654) : Remove support for commons fileupload


### Changes in 4.9.10 (5/Feb/25)

#### Bug

- [FELIX-6747](https://issues.apache.org/jira/browse/FELIX-6747) : NPE in activator of webconsole
- [FELIX-6751](https://issues.apache.org/jira/browse/FELIX-6751) : Use proper encoding for service filter


### Changes in 4.9.8 (14/Apr/24)                                                                                                                                                                                                                                          
#### Bug                                                                                                                                                                  

- [FELIX-6688](https://issues.apache.org/jira/browse/FELIX-6688) : Context path of outer servlet container is not respected for authentication

### Changes in 4.9.6 (7/Oct/23)

####

- [FELIX-6658](https://issues.apache.org/jira/browse/FELIX-6658) : URLs for status printers changed


### Changes in 4.9.4 (11/Sep/23)

#### Improvement

- [FELIX-6650](https://issues.apache.org/jira/browse/FELIX-6650) : Support id for SecurityProvider

#### Bug

- [FELIX-6649](https://issues.apache.org/jira/browse/FELIX-6649) : BundlesServlet replaces the previous bundle


### Changes in 4.9.2 (06/Sep/23)

#### Bug

- [FELIX-6645](https://issues.apache.org/jira/browse/FELIX-6645) : org.apache.felix.webconsole.spi.SecurityProvider impl is not picked for WebConsole authentication
- [FELIX-6646](https://issues.apache.org/jira/browse/FELIX-6646) : Plugin registered using AbstractServlet renders a blank page


### Changes in 4.9.0 (31/Aug/23)

#### New Feature

- [FELIX-6623](https://issues.apache.org/jira/browse/FELIX-6623) : Use Http Whiteboard for Web Console registration
- [FELIX-6626](https://issues.apache.org/jira/browse/FELIX-6626) : Support jakarta servlet registration

#### Task

- [FELIX-6639](https://issues.apache.org/jira/browse/FELIX-6639) : Deprecate all javax.servlet related API
- [FELIX-6624](https://issues.apache.org/jira/browse/FELIX-6624) : Clean up code

#### Improvement

- [FELIX-6356](https://issues.apache.org/jira/browse/FELIX-6356) : Set the shutdown and reload timeouts
- [FELIX-6630](https://issues.apache.org/jira/browse/FELIX-6630) : Make it possible to have newline characters in Felix WebConsole OSGi configuration property descriptions
- [FELIX-6637](https://issues.apache.org/jira/browse/FELIX-6637) : Migrate ConfigurationPrinter service to Inventory

#### Bug

- [FELIX-6629](https://issues.apache.org/jira/browse/FELIX-6629) : Cannot create factory configuration when Felix WebConsole is running in Tomcat


### Changes in 4.8.12 (04/Aug/23)

#### Bug

- [FELIX-6621](https://issues.apache.org/jira/browse/FELIX-6621) : Regressions caused by FELIX-6607


### Changes in 4.8.10 (23/Jul/23)

#### Improvement

- [FELIX-6607](https://issues.apache.org/jira/browse/FELIX-6607) : Web Console Plugins should have a predictable order in case of label conflicts

#### Bug

- [FELIX-6614](https://issues.apache.org/jira/browse/FELIX-6614) : WebConsole configMgr saves an empty value in list properites


### Changes in 4.8.8 (24/Feb/23)

#### Improvement

- [FELIX-6595](https://issues.apache.org/jira/browse/FELIX-6595) : Update to Commons Fileupload 1.5

### Changes in 4.8.4 (05/Sep/22)

#### Improvement

- [FELIX-6561](https://issues.apache.org/jira/browse/FELIX-6561) : Vulnerabilities in jquery-ui-1.12.1.js

#### Bug

- [FELIX-6563](https://issues.apache.org/jira/browse/FELIX-6563) : Regression - Webconsole REST API doesn't return Confguration PID when creating a new configuration

### Changes in 4.8.2 (23/May/22)

#### Improvement

- [FELIX-6532](https://issues.apache.org/jira/browse/FELIX-6532) : Remove dependency to commons-io
- [FELIX-6508](https://issues.apache.org/jira/browse/FELIX-6508) : Consume Servlet API Version Range (2.x,5] in WebConsole

#### Bug

- [FELIX-6531](https://issues.apache.org/jira/browse/FELIX-6531) : Web Console 4.8.0 does not generate metatype configurations for services


### Changes in 4.8.0 (16/May/22)

#### Improvement

- [FELIX-6500](https://issues.apache.org/jira/browse/FELIX-6500) : Enhance configuration SPI to filter configurations

#### Bug

- [FELIX-6503](https://issues.apache.org/jira/browse/FELIX-6503) : Webconsole doesn't set configuration property to default value


### Changes in 4.7.2 (18/Dec/21)

#### Improvement

- [FELIX-6319](https://issues.apache.org/jira/browse/FELIX-6319) : Expose all framework properties in a web console plugin
- [FELIX-6464](https://issues.apache.org/jira/browse/FELIX-6464) : Add Web Console plugin exposing Capabilities
- [FELIX-6040](https://issues.apache.org/jira/browse/FELIX-6040) : Configuration Manager does only expose OCD name which does not allow to disambiguate multiple designates pointing to the same OCD

#### Bug

- [FELIX-6466](https://issues.apache.org/jira/browse/FELIX-6466) : webconsole.configurationFactory.nameHint only detected via ConfigurationAdmin


### Changes in 4.7.0 (05/Sep/21)

#### Improvement

- [FELIX-6367](https://issues.apache.org/jira/browse/FELIX-6367) : Provide SPI for configuration management

#### Bug

- [FELIX-6453](https://issues.apache.org/jira/browse/FELIX-6453) : Change in configuration handling introduced by FELIX-6436


### Changes in 4.6.4 (25/Aug/21)

#### Improvement

- [FELIX-6436](https://issues.apache.org/jira/browse/FELIX-6436) : Exclude default values (from metatype) in Configuration


### Changes in 4.6.2 (13/Jun/21)

#### Improvement

- [FELIX-6428](https://issues.apache.org/jira/browse/FELIX-6428) : Provide a compatibility switch to enable password detection heuristic
- [FELIX-6427](https://issues.apache.org/jira/browse/FELIX-6427) : Obfuscate configuration properties marked as password in metatype in configuration printer
- [FELIX-6390](https://issues.apache.org/jira/browse/FELIX-6390) : Refactor the default authentication mechanism of the webconsole to be a WebConsoleSecurityProvider2
- [FELIX-6423](https://issues.apache.org/jira/browse/FELIX-6423) : Use property type password for password
- [FELIX-6424](https://issues.apache.org/jira/browse/FELIX-6424) : Update commons-io to 2.8.0

#### Bug

- [FELIX-6375](https://issues.apache.org/jira/browse/FELIX-6375) : Configuration Admin Service not available with org.apache.felix.webconsole_4.6.0.all
- [FELIX-6392](https://issues.apache.org/jira/browse/FELIX-6392) : Webconsole configadmin javascript error: Uncaught TypeError: parsers is undefined
- [FELIX-6371](https://issues.apache.org/jira/browse/FELIX-6371) : JSONConfigurationWriter does not escape backslash and other chars
- [FELIX-2715](https://issues.apache.org/jira/browse/FELIX-2715) : Configuration Admin Service jQuery error.


### Changes in 4.6.0 (17/Dec/20)

#### Improvement

- [FELIX-6366](https://issues.apache.org/jira/browse/FELIX-6366) : Update to jQuery 3.5.1 and jQuery migrate 3.3.0
- [FELIX-6363](https://issues.apache.org/jira/browse/FELIX-6363) : Simplify updating of OSGi configurations through REST API
- [FELIX-6370](https://issues.apache.org/jira/browse/FELIX-6370) : Provide a User interface for checks within plugins

#### Bug

- [FELIX-6341](https://issues.apache.org/jira/browse/FELIX-6341) : ConfigAdmin - deleting a configuration logs a string that should be translated
- [FELIX-6328](https://issues.apache.org/jira/browse/FELIX-6328) : Web Console (All In One) imports javax.portlet via fileupload


### Changes in 4.5.4 (20/Jul/20)

#### Bug

- [FELIX-6303](https://issues.apache.org/jira/browse/FELIX-6303) : Configuration console silently ignores invalid properties 

#### Improvement

- [FELIX-6299](https://issues.apache.org/jira/browse/FELIX-6299) : Prevent duplicate printing of configuration details


### Changes in 4.5.2 (08/May/20)

#### Bug

- [FELIX-6270](https://issues.apache.org/jira/browse/FELIX-6270) : webconsole-all bundle doesn't contain required package
- [FELIX-6271](https://issues.apache.org/jira/browse/FELIX-6271) : Make sure invalid bundles are deleted in BundleServlet


### Changes in 4.5.0 (27/Apr/20)

#### Improvement

- [FELIX-6254](https://issues.apache.org/jira/browse/FELIX-6254) : Package refresh does not attach fragments
- [FELIX-6255](https://issues.apache.org/jira/browse/FELIX-6255) : Allow to update a specific bundle through the UI
- [FELIX-6260](https://issues.apache.org/jira/browse/FELIX-6260) : Require Java 8 and OSGi R6
- [FELIX-6268](https://issues.apache.org/jira/browse/FELIX-6268) : Add a link to the configuration json serializer if available

### Changes in 4.4.0 (02/Apr/20)

#### Improvement

- [FELIX-6232](https://issues.apache.org/jira/browse/FELIX-) : Allow Webconsole to install parallel versions of bundles

#### Bug

- [FELIX-6185](https://issues.apache.org/jira/browse/FELIX-6185) : jQuery <3.4.0 is vulnerable to prototype pollution attacks
- [FELIX-5661](https://issues.apache.org/jira/browse/FELIX-5661) : The heuristic to derive the password type from the metatype id does not work reliably


### Changes in 4.3.16 (20/Aug/19)

#### Bug

- [FELIX-6171](https://issues.apache.org/jira/browse/FELIX-6171) : Webconsole OsgiManager throws NPE
- [FELIX-6172](https://issues.apache.org/jira/browse/FELIX-6172) : Already Registered Servlet Exception with WebConsole


### Changes in 4.3.14 (16/Aug/19)

#### Improvement

- [FELIX-6037](https://issues.apache.org/jira/browse/FELIX-6037) : Commons FileUpload 1.4 breaks bundle uploads
- [FELIX-6168](https://issues.apache.org/jira/browse/FELIX-6168) : Enable WebConsole login only after specified Security Providers are present


### Changes in 4.3.12 (27/May/19)

#### Bug

- [FELIX-6132](https://issues.apache.org/jira/browse/FELIX-6132) : XSS possible in service console


### Changes in 4.3.10 (20/May/19)

#### Improvement

- [FELIX-5934](https://issues.apache.org/jira/browse/FELIX-5934) : The web console stores unsalted hashed password

#### Bug

- [FELIX-6128](https://issues.apache.org/jira/browse/FELIX-6128) : Escape bundle name and manifest headers
- [FELIX-6127](https://issues.apache.org/jira/browse/FELIX-6127) : Escape name hint in configuration listing


### Changes in 4.3.8 (11/Sep/18)

#### Improvement

- [FELIX-5901](https://issues.apache.org/jira/browse/FELIX-5901) : Update to latest jQuery UI 1.12.1

#### Bug

- [FELIX-5893](https://issues.apache.org/jira/browse/FELIX-5893) : JQuery Security bug CVE-2015-9251 in Web Console


### Changes in 4.3.4 (12/May/17)

#### Bug

- [FELIX-5638](https://issues.apache.org/jira/browse/FELIX-5638) : Unintended Web Console RESTful API change


### Changes in 4.3.2 (09/May/17)

#### Bug

- [FELIX-5620](https://issues.apache.org/jira/browse/FELIX-5620) : Bundle start/stop buttons are missing

#### Improvement

- [FELIX-5566](https://issues.apache.org/jira/browse/FELIX-5566) : Stop/update/refresh/start bundles when updating through the webconsole for faster turnaround
- [FELIX-5602](https://issues.apache.org/jira/browse/FELIX-5602) : Use Java 6 as base java version


### Changes in 4.3.0 (17/Feb/17)

#### Improvement

- [FELIX-5504](https://issues.apache.org/jira/browse/FELIX-5504) : Switch from org.json to new simple json writer in utils
- [FELIX-5509](https://issues.apache.org/jira/browse/FELIX-5509) : Remove method keyVal from WebConsoleUtil


### Changes in 4.2.18 (10/Jan/17)

#### Bug

- [FELIX-4840](https://issues.apache.org/jira/browse/FELIX-4840) : Asynchronous IO fails in webconsole plugin
- [FELIX-5387](https://issues.apache.org/jira/browse/FELIX-5387) : NPE for requests missing 'felix-webconsole-locale' cookie

#### Improvement

- [FELIX-5445](https://issues.apache.org/jira/browse/FELIX-5445) : Web Console: Properly display non-string property arrays in name hint
- [FELIX-5484](https://issues.apache.org/jira/browse/FELIX-5484) : Webconsole configuration factory name hint array rendering


### Changes in 4.2.16 (03/Jun/16)

#### Bug

- [FELIX-4941](https://issues.apache.org/jira/browse/FELIX-4941) : Configuration Properties not defined in Metatype are lost after update
- [FELIX-4795](https://issues.apache.org/jira/browse/FELIX-4795) : Servlet API 3.x not supported 
- [FELIX-5223](https://issues.apache.org/jira/browse/FELIX-5223) : IE11 and Edge: Fields in OSGI Configuration Manager are not editable 


### Changes in 4.2.14 (06/Oct/15)

#### Bug

- [FELIX-5042](https://issues.apache.org/jira/browse/FELIX-5042) : Get system bundle by location, not number for global bundle list
- [FELIX-5060](https://issues.apache.org/jira/browse/FELIX-5060) : Unnecessary import of org.osgi.service.component


### Changes in 4.2.12 (23/Sep/15)

#### Bug

- [FELIX-2880](https://issues.apache.org/jira/browse/FELIX-2880) : The Webconsole does not handle Vector<PrimitiveWrapper>
- [FELIX-3366](https://issues.apache.org/jira/browse/FELIX-3366) : The Configuration Webconsole Plugin seems to ignore the ocd ref element
- [FELIX-4849](https://issues.apache.org/jira/browse/FELIX-4849) : Do not call setContentLength after calling sendRedirect
- [FELIX-4995](https://issues.apache.org/jira/browse/FELIX-4995) : NPE when updating Apache Felix OSGi Management Console configuration without a password
- [FELIX-5004](https://issues.apache.org/jira/browse/FELIX-5004) : Null is passed to BundleInfoProvider if plugin root is null
- [FELIX-5031](https://issues.apache.org/jira/browse/FELIX-5031) : NPE in Web Console configuration plugin when metatype service is missing
- [FELIX-5041](https://issues.apache.org/jira/browse/FELIX-5041) : Cannot build web console with JDK 1.8 due to javadoc problems

#### Improvement

- [FELIX-5018](https://issues.apache.org/jira/browse/FELIX-5018) : "create factory configuration" link for config screen
- [FELIX-5019](https://issues.apache.org/jira/browse/FELIX-5019) : "referer" parameter for configuration open/create link


### Changes in 4.2.10 (20/Jul/15)

#### Bug

- [FELIX-4852](https://issues.apache.org/jira/browse/FELIX-4852) : Unbinding configuration does not have desired effect
- [FELIX-4886](https://issues.apache.org/jira/browse/FELIX-4886) : Check for ConfigAdmin#listConfigurations returning null


### Changes in 4.2.8 (16/Mar/15)

#### Bug

- [FELIX-3695](https://issues.apache.org/jira/browse/FELIX-3695) : When bundle filter doesn't match, the tool bars are duplicated
- [FELIX-4800](https://issues.apache.org/jira/browse/FELIX-4800) : Bundle search in /system/console/bundles produces 405

#### Improvement

- [FELIX-3006](https://issues.apache.org/jira/browse/FELIX-3006) : Please create a logout button for the web console screen


### Changes in 4.2.6 (30/Jan/15)

#### Bug

- [FELIX-4203](https://issues.apache.org/jira/browse/FELIX-4203) : ConfigAdmin plugin does not return json
- [FELIX-4734](https://issues.apache.org/jira/browse/FELIX-4734) : Web Console RESTful API should wait for asynchonous operations until they complete 
- [FELIX-4735](https://issues.apache.org/jira/browse/FELIX-4735) : Cannot create a new factory configuration from Web Admin Console

#### Improvement

- [FELIX-3849](https://issues.apache.org/jira/browse/FELIX-3849) : Support setting configuration binding
- [FELIX-4007](https://issues.apache.org/jira/browse/FELIX-4007) : enable multiline inputs in Web Console Configuration
- [FELIX-4704](https://issues.apache.org/jira/browse/FELIX-4704) : Show ranking in web console services plugin
- [FELIX-4710](https://issues.apache.org/jira/browse/FELIX-4710) : Web Console: Display templated name hint for factory configuration entries
- [FELIX-4737](https://issues.apache.org/jira/browse/FELIX-4737) : Provide an option to use system bundle context to get bundles/services (Support for subsystems)
- [FELIX-4746](https://issues.apache.org/jira/browse/FELIX-4746) : Escape outputting filter parameter in service servlet

#### Task

- [FELIX-4738](https://issues.apache.org/jira/browse/FELIX-4738) : Deprecate WebConsoleUtil#keyVal


### Changes in 4.2.4 (12/Dec/14)

#### Bug

- [FELIX-3817](https://issues.apache.org/jira/browse/FELIX-3817) : Form parameters might clash with configuration parameters
- [FELIX-4558](https://issues.apache.org/jira/browse/FELIX-4558) : Web Console Service plugin doesn&#39;t list properties with value 0
- [FELIX-4562](https://issues.apache.org/jira/browse/FELIX-4562) : Web Console License plugin fails to load files
- [FELIX-4572](https://issues.apache.org/jira/browse/FELIX-4572) : Web Console may cause NPE on refresh packages
- [FELIX-4610](https://issues.apache.org/jira/browse/FELIX-4610) : WebConsole doesn&#39;t start with Java Security enabled
- [FELIX-4652](https://issues.apache.org/jira/browse/FELIX-4652) : Security problem with AbstractWebConsolePlugin.spoolResource
- [FELIX-4660](https://issues.apache.org/jira/browse/FELIX-4660) : Security problem in WebConsoleUtil.getParameter() method
- [FELIX-4662](https://issues.apache.org/jira/browse/FELIX-4662) : WebConsole Xdialog javascript function is not working correctly
                
#### New Feature

- [FELIX-3848](https://issues.apache.org/jira/browse/FELIX-3848) : Differentiate between unbound and new configuration
- [FELIX-4711](https://issues.apache.org/jira/browse/FELIX-4711) : Web Console: False AJAX error displayed on deleting or unbinding config


### Changes in 4.2.2 (06/Feb/14)

#### Bug

- [FELIX-4301](https://issues.apache.org/jira/browse/FELIX-4301) : Updated configuration is transmitted as query string to the request URL instead of POST payload
- [FELIX-4299](https://issues.apache.org/jira/browse/FELIX-4299) : Support hashed password
- [FELIX-4187](https://issues.apache.org/jira/browse/FELIX-4187) : Configuration Plugin does a POST to get configuration details

#### Improvement

- [FELIX-4202](https://issues.apache.org/jira/browse/FELIX-4202) : Allow to filter services using ldap filter in webconsole services tab
- [FELIX-3861](https://issues.apache.org/jira/browse/FELIX-3861) : Set felix.webconsole.category on Web Console plugins


### Changes in 4.2.0 (05/Jun/13)

#### Bug

- [FELIX-3318](https://issues.apache.org/jira/browse/FELIX-3318) : NPE in WebConsole when trying to uninstall a bundle
- [FELIX-3570](https://issues.apache.org/jira/browse/FELIX-3570) : Commons IO dependency issue
- [FELIX-3605](https://issues.apache.org/jira/browse/FELIX-3605) : Configuration Status plugin has an error in IE/Compatibility View
- [FELIX-3663](https://issues.apache.org/jira/browse/FELIX-3663) : Java Runtime in the System Information tab appears as 'null(build null)' in Skelmir CEE-J VM
- [FELIX-3666](https://issues.apache.org/jira/browse/FELIX-3666) : NPE when uninstalling a bundle
- [FELIX-3694](https://issues.apache.org/jira/browse/FELIX-3694) : In some cases web console cannot edit configs without metatype
- [FELIX-3783](https://issues.apache.org/jira/browse/FELIX-3783) : ConfigurationAdminConfigurationPrinter failing if Configuration Admin API not bound
- [FELIX-3784](https://issues.apache.org/jira/browse/FELIX-3784) : Configuration Admin tab not working when Metatype Service API is missing
- [FELIX-3795](https://issues.apache.org/jira/browse/FELIX-3795) : JS Error in Configuration Status printing
- [FELIX-3814](https://issues.apache.org/jira/browse/FELIX-3814) : Javascript error when directly invoking a configuration with IE8
- [FELIX-3829](https://issues.apache.org/jira/browse/FELIX-3829) : NullPointerException when creating configuration with multi-value password properties
- [FELIX-3841](https://issues.apache.org/jira/browse/FELIX-3841) : CSS issues in IE with Web Console menus
- [FELIX-3936](https://issues.apache.org/jira/browse/FELIX-3936) : Main menu items hidden in the Configuration Status page
- [FELIX-3946](https://issues.apache.org/jira/browse/FELIX-3946) : NullPointerException in BundleServlet.bundleDetails when not called through HTTP
- [FELIX-3965](https://issues.apache.org/jira/browse/FELIX-3965) : threads configuration status generates invalid results
- [FELIX-3969](https://issues.apache.org/jira/browse/FELIX-3969) : web console shows invalid bundle symbolic name
- [FELIX-3986](https://issues.apache.org/jira/browse/FELIX-3986) : ThreadDumper comparators are not correctly implemented
- [FELIX-4074](https://issues.apache.org/jira/browse/FELIX-4074) : Plugin class name changed for config manager plugin, might be disabled on update
 
#### Improvement

- [FELIX-2234](https://issues.apache.org/jira/browse/FELIX-2234) : Reduce status information from bundle plugin
- [FELIX-3594](https://issues.apache.org/jira/browse/FELIX-3594) : Service should be sorted by ID in service configuration printer
- [FELIX-3798](https://issues.apache.org/jira/browse/FELIX-3798) : Make default category configurable
- [FELIX-3799](https://issues.apache.org/jira/browse/FELIX-3799) : Sort plugin plugin links by title
- [FELIX-3851](https://issues.apache.org/jira/browse/FELIX-3851) : Allow for refresh after bundle update/install
- [FELIX-3861](https://issues.apache.org/jira/browse/FELIX-3861) : Set felix.webconsole.category on Web Console plugins
- [FELIX-3951](https://issues.apache.org/jira/browse/FELIX-3951) : Let users able to specify the directory used to temporarily store uploaded files

#### New Feature

- [FELIX-2896](https://issues.apache.org/jira/browse/FELIX-2896) : Add support for bundle info providers
- [FELIX-3769](https://issues.apache.org/jira/browse/FELIX-3769) : Improve the way Web Console UI manages growing number of plugins.
- [FELIX-3770](https://issues.apache.org/jira/browse/FELIX-3770) : Upgrade jquery-ui to 1.9.1

#### Task

- [FELIX-3778](https://issues.apache.org/jira/browse/FELIX-3778) : Create all-in-one WebConsole build again
- [FELIX-3833](https://issues.apache.org/jira/browse/FELIX-3833) : Consider backwards compatibility break with label map due to Categories
- [FELIX-3834](https://issues.apache.org/jira/browse/FELIX-3834) : Create separate language fragment bundles
- [FELIX-3874](https://issues.apache.org/jira/browse/FELIX-3874) : Create new status printer module


### Changes in 4.0.0 (10/Jun/12)

#### Bug

- [FELIX-1865](https://issues.apache.org/jira/browse/FELIX-1865) : Metatype resolving mechanism does not seem to work correctly
- [FELIX-2707](https://issues.apache.org/jira/browse/FELIX-2707) : Webconsole does not resolve resource bundle properly
- [FELIX-2708](https://issues.apache.org/jira/browse/FELIX-2708) : Webconsole causes NPE in Bundle.getResource(String)
- [FELIX-2727](https://issues.apache.org/jira/browse/FELIX-2727) : WebConsole logs an error not being able to create the DepPackServlet
- [FELIX-2830](https://issues.apache.org/jira/browse/FELIX-2830) : Tablesorter loses it's styling if placed in JQuery TAB component
- [FELIX-2855](https://issues.apache.org/jira/browse/FELIX-2855) : WebConsole cannot read the list of enabled plugins when using FileInstall
- [FELIX-2879](https://issues.apache.org/jira/browse/FELIX-2879) : Metatype Service confuses the id of the OCD with the Designate.pid/factoryPid.
- [FELIX-2889](https://issues.apache.org/jira/browse/FELIX-2889) : Invalid JSON content in http response after starting or stopping a bundle.
- [FELIX-2971](https://issues.apache.org/jira/browse/FELIX-2971) : Configuration changes cannot be made via Felix Web Console in IE7
- [FELIX-2979](https://issues.apache.org/jira/browse/FELIX-2979) : Declarative Services Components page only displayed if Configuration Admin and Metatype Service API is available
- [FELIX-3010](https://issues.apache.org/jira/browse/FELIX-3010) : XSS in Felix Web Console
- [FELIX-3028](https://issues.apache.org/jira/browse/FELIX-3028) : Better handle configuration unbinding
- [FELIX-3116](https://issues.apache.org/jira/browse/FELIX-3116) : Saving configuration shows "AJAX error" dialog
- [FELIX-3132](https://issues.apache.org/jira/browse/FELIX-3132) : Extensions not properly supported
- [FELIX-3268](https://issues.apache.org/jira/browse/FELIX-3268) : Cannot build webconsole and webconsole-plugins with JDK 7
- [FELIX-3284](https://issues.apache.org/jira/browse/FELIX-3284) : NullPointerException may be thrown if asynchronous bundle update fails
- [FELIX-3285](https://issues.apache.org/jira/browse/FELIX-3285) : Wrong vector and array handling in Configuration Admin plugin
- [FELIX-3311](https://issues.apache.org/jira/browse/FELIX-3311) : Cookie handling seems not to work anymore
- [FELIX-3320](https://issues.apache.org/jira/browse/FELIX-3320) : WebConsole UX: actions and status on bundle details don't update properly
- [FELIX-3404](https://issues.apache.org/jira/browse/FELIX-3404) : Web Admin Log Plugin stops listing of log entries if message is null.
- [FELIX-3405](https://issues.apache.org/jira/browse/FELIX-3405) : NPE in Web Console
- [FELIX-3406](https://issues.apache.org/jira/browse/FELIX-3406) : Localization in Web Console doesn't work anymore
- [FELIX-3408](https://issues.apache.org/jira/browse/FELIX-3408) : Web Console date chooser shows garbage text
- [FELIX-3433](https://issues.apache.org/jira/browse/FELIX-3433) : WebConsole default ajax error handlers doesn't work with IE
- [FELIX-3469](https://issues.apache.org/jira/browse/FELIX-3469) : Type mismatch JS error using the memoryusage plugin
- [FELIX-3473](https://issues.apache.org/jira/browse/FELIX-3473) : web console config manager plugin no longer works with J9
- [FELIX-3485](https://issues.apache.org/jira/browse/FELIX-3485) : ui-bg_glass_55_fbf9ee_1x400.png does not exist in admin_compat.css

#### Improvement

- [FELIX-2117](https://issues.apache.org/jira/browse/FELIX-2117) : Use DynamicImport instead of Optional Packages
- [FELIX-2697](https://issues.apache.org/jira/browse/FELIX-2697) : Allow enable/disable internal plugins dynamically
- [FELIX-3014](https://issues.apache.org/jira/browse/FELIX-3014) : Have a way to "deep link" to a particular tab within Configuration Status
- [FELIX-3022](https://issues.apache.org/jira/browse/FELIX-3022) : Add Uptime indication to Status page
- [FELIX-3024](https://issues.apache.org/jira/browse/FELIX-3024) : Add Delete and Unbind buttons to Configuration Detail dialogs
- [FELIX-3025](https://issues.apache.org/jira/browse/FELIX-3025) : Add a configuration status list with a short bundle list
- [FELIX-3027](https://issues.apache.org/jira/browse/FELIX-3027) : Make download links for single configuration status file (or ZIP file) more prominent
- [FELIX-3040](https://issues.apache.org/jira/browse/FELIX-3040) : Configuration status dump should contain a timestamp when the dump has been taken
- [FELIX-3099](https://issues.apache.org/jira/browse/FELIX-3099) : Separate Deployment Admin plugin
- [FELIX-3100](https://issues.apache.org/jira/browse/FELIX-3100) : Separate SCR plugin
- [FELIX-3107](https://issues.apache.org/jira/browse/FELIX-3107) : Separate Shell Plugin
- [FELIX-3111](https://issues.apache.org/jira/browse/FELIX-3111) : Separate OBR Plugin
- [FELIX-3168](https://issues.apache.org/jira/browse/FELIX-3168) : Add support for new PASSWORD attribute type of Metatype service
- [FELIX-3236](https://issues.apache.org/jira/browse/FELIX-3236) : Make language selection cookie longer lasting
- [FELIX-3282](https://issues.apache.org/jira/browse/FELIX-3282) : Generate default fields for configurations without descriptor
- [FELIX-3290](https://issues.apache.org/jira/browse/FELIX-3290) : Improve Cookie handling of the Web Console
- [FELIX-3298](https://issues.apache.org/jira/browse/FELIX-3298) : Add animal sniffer plugin to ensure no using any non-Java 1.4 API
- [FELIX-3315](https://issues.apache.org/jira/browse/FELIX-3315) : Log plugin does not show the bundle that has logged the event
- [FELIX-3316](https://issues.apache.org/jira/browse/FELIX-3316) : Log plugin should provide more detailed exception column
- [FELIX-3417](https://issues.apache.org/jira/browse/FELIX-3417) : Web Console Inconsistent status text
- [FELIX-3418](https://issues.apache.org/jira/browse/FELIX-3418) : Sort threads by name in Configuration Status -> Threads
- [FELIX-3482](https://issues.apache.org/jira/browse/FELIX-3482) : Text wrapping goes out of bounds in Configuration Status
- [FELIX-3491](https://issues.apache.org/jira/browse/FELIX-3491) : Better activity indication when pressing refresh/update bundle buttons 
- [FELIX-3502](https://issues.apache.org/jira/browse/FELIX-3502) : Improve Threads web console printer

#### New Feature

- [FELIX-2709](https://issues.apache.org/jira/browse/FELIX-2709) : Allow webconsole context root be obtained from framework properties

#### Task

- [FELIX-2904](https://issues.apache.org/jira/browse/FELIX-2904) : Inlined required/embedded dependencies
- [FELIX-3279](https://issues.apache.org/jira/browse/FELIX-3279) : Drop default build and use bare profile as the single build
- [FELIX-3280](https://issues.apache.org/jira/browse/FELIX-3280) : Update to use parent POM 2.1
- [FELIX-3281](https://issues.apache.org/jira/browse/FELIX-3281) : Use bundle plugin 2.3.6 and BND annotations for package export
- [FELIX-3490](https://issues.apache.org/jira/browse/FELIX-3490) : Notice included OSGi classes


### Changes in  3.1.8 (	07/Feb/11)

#### Bug

- [FELIX-2713](https://issues.apache.org/jira/browse/FELIX-2713) : Problem in HtmlConfigurationWriter
- [FELIX-2729](https://issues.apache.org/jira/browse/FELIX-2729) : Webconsole - Configuration fails to print configuration for bundles without MetatypeService config
- [FELIX-2735](https://issues.apache.org/jira/browse/FELIX-2735) : ClassCastException in PermissionsConfigurationPrinter
- [FELIX-2793](https://issues.apache.org/jira/browse/FELIX-2793) : Plugin request is not detected as html request if context path contains a dot
- [FELIX-2795](https://issues.apache.org/jira/browse/FELIX-2795) : Parameters are not removed from symbolic name when installing a bundle


### Changes in 3.1.6 (08/Nov/10)

#### Bug

- [FELIX-2570](https://issues.apache.org/jira/browse/FELIX-2570) : HTML is escaped in ModeAwareConfigurationPrinter when in &quot;web&quot; mode
- [FELIX-2573](https://issues.apache.org/jira/browse/FELIX-2573) : switching the console language bugs the jqueryUI datepicker component
- [FELIX-2609](https://issues.apache.org/jira/browse/FELIX-2609) : WebConsole doesn't work with JDK 1.3.1_06 because of Locale
- [FELIX-2610](https://issues.apache.org/jira/browse/FELIX-2610) :  WebConsole doesn't work with JDK 1.3.1_06 because of problem in MessageFormat
- [FELIX-2617](https://issues.apache.org/jira/browse/FELIX-2617) : webconsole has option to change the language but doesn't show the currently selected one
- [FELIX-2627](https://issues.apache.org/jira/browse/FELIX-2627) : can't install war files via webconsole
- [FELIX-2635](https://issues.apache.org/jira/browse/FELIX-2635) : PluginHolder.setServletContext() must nullify servlet context after plugins destroying 
- [FELIX-2644](https://issues.apache.org/jira/browse/FELIX-2644) : cannot disable plugin
- [FELIX-2650](https://issues.apache.org/jira/browse/FELIX-2650) : ConfigurationPrinter with other modes than web should be excluded from web
- [FELIX-2658](https://issues.apache.org/jira/browse/FELIX-2658) : Deprecate ConfigurationPrinter.PROPERTY_MODES constant
- [FELIX-2659](https://issues.apache.org/jira/browse/FELIX-2659) : ConfigurationRender.searchMethod must catch problems more broadly

#### Improvement

- [FELIX-2541](https://issues.apache.org/jira/browse/FELIX-) : Licenses Page : Add support for DEPENDENCIES files
- [FELIX-2614](https://issues.apache.org/jira/browse/FELIX-) : Mark unresolved packages
- [FELIX-2616](https://issues.apache.org/jira/browse/FELIX-) : Russian l10n files for Web Console
- [FELIX-2639](https://issues.apache.org/jira/browse/FELIX-) : Improve Security Provider support
- [FELIX-2652](https://issues.apache.org/jira/browse/FELIX-) : Allow attachment providers which do not implement the interface
- [FELIX-2660](https://issues.apache.org/jira/browse/FELIX-) : Prevent Bundle ConfigurationPrinter from being used in the web output
- [FELIX-2668](https://issues.apache.org/jira/browse/FELIX-) : Add localization for the meta-type
- [FELIX-2674](https://issues.apache.org/jira/browse/FELIX-) : Too much error logging after fixing FELIX-2644

#### New Feature

- [FELIX-2638](https://issues.apache.org/jira/browse/FELIX-2638) : Make a single configuration printer output available via http
- [FELIX-2649](https://issues.apache.org/jira/browse/FELIX-2649) : Support for configuration printers without requiring them to implement the interface

#### Task

- [FELIX-2540](https://issues.apache.org/jira/browse/FELIX-2540) : Ensure inclusion of the DEPENDENCIES file in the build artifacts
- [FELIX-2684](https://issues.apache.org/jira/browse/FELIX-2684) : Fix legal files and add a changelog.txt


### Changes in 3.1.2 (16/Aug/10)

#### Task

- [FELIX-2412](https://issues.apache.org/jira/browse/FELIX-2412) : Update Web Console legal files

#### Bug

- [FELIX-2287](https://issues.apache.org/jira/browse/FELIX-2287) : Webcosole: showing all resources from a repository doesn't work
- [FELIX-2288](https://issues.apache.org/jira/browse/FELIX-2288) : Felix SCR API problem/misunderstanding
- [FELIX-2299](https://issues.apache.org/jira/browse/FELIX-2299) : OBR web console plugin doesn't show some bundles as installed
- [FELIX-2424](https://issues.apache.org/jira/browse/FELIX-2424) : Internal Server Error requesting /system/console or /system/console/
- [FELIX-2447](https://issues.apache.org/jira/browse/FELIX-2447) : Clicking on a service link does not display the service details
- [FELIX-2448](https://issues.apache.org/jira/browse/FELIX-2448) : PermissionAdmin and WireAdmin plugins cause internal server error if service API is missing
- [FELIX-2470](https://issues.apache.org/jira/browse/FELIX-2470) : No class def found error - Wire Admin (see screenshot)
- [FELIX-2471](https://issues.apache.org/jira/browse/FELIX-2471) : No class def found error - Permissions Admin (see screenshot)
- [FELIX-2508](https://issues.apache.org/jira/browse/FELIX-2508) : Web Console does not show all components
- [FELIX-2535](https://issues.apache.org/jira/browse/FELIX-2535) : Cyrillic characters are not displayed properly.

#### Improvement

- [FELIX-2240](https://issues.apache.org/jira/browse/FELIX-2240) : Web Console should allow user to select http service to bind to
- [FELIX-2277](https://issues.apache.org/jira/browse/FELIX-2277) : Allow the user to select display language
- [FELIX-2282](https://issues.apache.org/jira/browse/FELIX-2282) : Optimize Services Printer
- [FELIX-2391](https://issues.apache.org/jira/browse/FELIX-2391) : Potential dead locking issue in OsgiManager.init
- [FELIX-2509](https://issues.apache.org/jira/browse/FELIX-2509) : Render more data for component details


### Changes in 3.1.0 (18/Jun/10)

#### Bug

- [FELIX-2243](https://issues.apache.org/jira/browse/FELIX-2243) : Bundle Plugin 'Find All&quot; button generates error with Opera
- [FELIX-2244](https://issues.apache.org/jira/browse/FELIX-2244) : Bundles Printer fails when imports contains range.
- [FELIX-2256](https://issues.apache.org/jira/browse/FELIX-2256) : Some small visual defects in the WebConsole
- [FELIX-2257](https://issues.apache.org/jira/browse/FELIX-2257) : Bundle sort order is not stored in a cookie anymore
- [FELIX-2260](https://issues.apache.org/jira/browse/FELIX-2260) : Potential NullPointerException in ServicesServlet.writeJSON()
- [FELIX-2261](https://issues.apache.org/jira/browse/FELIX-2261) : On the Servlets page the list of using bundles is not displayed
- [FELIX-2263](https://issues.apache.org/jira/browse/FELIX-2263) : OsgiManager servlet should commit response
- [FELIX-2272](https://issues.apache.org/jira/browse/FELIX-2272) : SCR plugin shows misleading message
- [FELIX-2274](https://issues.apache.org/jira/browse/FELIX-2274) : 404 when plugin is missing
- [FELIX-2285](https://issues.apache.org/jira/browse/FELIX-2285) : Bundles Plugin doesn't render the bundles name in IE
- [FELIX-2286](https://issues.apache.org/jira/browse/FELIX-2286) : Various rendering issues with IE
- [FELIX-2338](https://issues.apache.org/jira/browse/FELIX-2338) : Problem in the Configuration Render
- [FELIX-2390](https://issues.apache.org/jira/browse/FELIX-2390) : WebConsole Config Manager problem

#### Improvement

- [FELIX-1141](https://issues.apache.org/jira/browse/FELIX-1141) : Provide feedback for operations
- [FELIX-2207](https://issues.apache.org/jira/browse/FELIX-2207) : License plugin should support Bundle-License manifest header
- [FELIX-2245](https://issues.apache.org/jira/browse/FELIX-2245) : Log Plugin - level sorting should be by level
- [FELIX-2246](https://issues.apache.org/jira/browse/FELIX-2246) : Lazy initialization of plugins
- [FELIX-2250](https://issues.apache.org/jira/browse/FELIX-2250) : Security Policy configuration printer
- [FELIX-2251](https://issues.apache.org/jira/browse/FELIX-2251) : Wire Admin configuration printer
- [FELIX-2253](https://issues.apache.org/jira/browse/FELIX-2253) : Display progress indiciator on configuration status page
- [FELIX-2259](https://issues.apache.org/jira/browse/FELIX-2259) : ServicesServlet should use BundleContext.getAllServiceReferences instead of getServiceReferences
- [FELIX-2267](https://issues.apache.org/jira/browse/FELIX-2267) : Improved locale detection
- [FELIX-2284](https://issues.apache.org/jira/browse/FELIX-2284) : Add common utility method for converting object (array) to string
- [FELIX-2291](https://issues.apache.org/jira/browse/FELIX-2291) : Show available number of processors on the System Information page

#### New Feature

- [FELIX-1764](https://issues.apache.org/jira/browse/FELIX-1764) : Add support for pluggable access control


### Changes in 3.0.0 (31/Mar/10)

#### Bug

- [FELIX-1992](https://issues.apache.org/jira/browse/FELIX-1992) : Change the use of the &#xA7; character as a separator in the BundleRepositoryRender class
- [FELIX-2005](https://issues.apache.org/jira/browse/FELIX-2005) : Configuration done using &quot;org.apache.felix.webconsole.internal.servlet.OsgiManager&quot; PID is not used by WebConsole
- [FELIX-2007](https://issues.apache.org/jira/browse/FELIX-2007) : JavaScript error on bundle page
- [FELIX-2009](https://issues.apache.org/jira/browse/FELIX-2009) : Reconfiguring the web console location fails
- [FELIX-2022](https://issues.apache.org/jira/browse/FELIX-2022) : Branding - webconsole.product.image not treated correctly when referencing http resource
- [FELIX-2023](https://issues.apache.org/jira/browse/FELIX-2023) : Branding - webconsole.product.name is not used in HTML page title and header tags
- [FELIX-2029](https://issues.apache.org/jira/browse/FELIX-2029) : Support for &quot;default&quot; locale does not work
- [FELIX-2034](https://issues.apache.org/jira/browse/FELIX-2034) : WebConsole fails to register if Http Service is registered after Web Console start
- [FELIX-2086](https://issues.apache.org/jira/browse/FELIX-2086) : Use a different symbolic name for the bare web console bundle
- [FELIX-2105](https://issues.apache.org/jira/browse/FELIX-2105) : Make Web Console compatible with OSGi/Minimum-1.1 EE
- [FELIX-2118](https://issues.apache.org/jira/browse/FELIX-2118) : IE7 does not properly load license files into &lt;pre&gt; element
- [FELIX-2119](https://issues.apache.org/jira/browse/FELIX-2119) : Bundle update fails, if OBR is not installed
- [FELIX-2120](https://issues.apache.org/jira/browse/FELIX-2120) : OBR plugin should hide the repository table, if OBR service is not available
- [FELIX-2122](https://issues.apache.org/jira/browse/FELIX-2122) : Possible NullPointerException reporting failure to instantiate a plugin
- [FELIX-2123](https://issues.apache.org/jira/browse/FELIX-2123) : Latest commit to ConfigurationRender is not OSGi/Minumum-1.0 compatible
- [FELIX-2124](https://issues.apache.org/jira/browse/FELIX-2124) : remove internal usage of deprecated Action interface
- [FELIX-2142](https://issues.apache.org/jira/browse/FELIX-2142) : The Config Manager doesn't show configurations which values are pritive arrays
- [FELIX-2147](https://issues.apache.org/jira/browse/FELIX-2147) : ConfigurationPrinter services not unregistered on destroy
- [FELIX-2149](https://issues.apache.org/jira/browse/FELIX-2149) : Configuration Status tabs are not properly left aligned
- [FELIX-2157](https://issues.apache.org/jira/browse/FELIX-2157) : Login dialog delay
- [FELIX-2183](https://issues.apache.org/jira/browse/FELIX-2183) : Global AJAX error handler can be improved
- [FELIX-2188](https://issues.apache.org/jira/browse/FELIX-2188) : Layout problems with old pluggins
- [FELIX-2198](https://issues.apache.org/jira/browse/FELIX-2198) : ConfigManager plugin logs NullPointerException if ConfigurationAdmin Service is not availble
- [FELIX-2204](https://issues.apache.org/jira/browse/FELIX-2204) : Localization causes a problem in WebConsole configuration
- [FELIX-2206](https://issues.apache.org/jira/browse/FELIX-2206) : Localization causes a problem in Configuration Printers
- [FELIX-2214](https://issues.apache.org/jira/browse/FELIX-2214) : Event plugin - template &amp; localization files must be UTF-8.
- [FELIX-2216](https://issues.apache.org/jira/browse/FELIX-2216) : Web console needs to be able to use service bundles like obr installed after it
- [FELIX-2219](https://issues.apache.org/jira/browse/FELIX-2219) : Showing bundle details of an uninstalled bundles reports &quot;Internal Server Error&quot;
- [FELIX-2235](https://issues.apache.org/jira/browse/FELIX-2235) : Deployment plugin fails with internal server error

#### Improvement

- [FELIX-567](https://issues.apache.org/jira/browse/FELIX-567) : Search for exported/imported packages
- [FELIX-569](https://issues.apache.org/jira/browse/FELIX-569) : Improve Configuration Page
- [FELIX-957](https://issues.apache.org/jira/browse/FELIX-957) : Migrate Bundle Repository Page to JQuery
- [FELIX-1051](https://issues.apache.org/jira/browse/FELIX-1051) : Localize the web console
- [FELIX-1910](https://issues.apache.org/jira/browse/FELIX-1910) : Normal URLs instead of Javascript links in Licenses screen
- [FELIX-1956](https://issues.apache.org/jira/browse/FELIX-1956) : Collect duplicate &amp; reusable code in WebConsoleUtil
- [FELIX-1958](https://issues.apache.org/jira/browse/FELIX-1958) : Branding L&amp;F issues 
- [FELIX-1993](https://issues.apache.org/jira/browse/FELIX-1993) : Enhance configuration printer support
- [FELIX-1996](https://issues.apache.org/jira/browse/FELIX-1996) : Console should warn when config is bound to a different bundle
- [FELIX-2017](https://issues.apache.org/jira/browse/FELIX-2017) : Potential NullPointerException when running Web Console in Equinox
- [FELIX-2099](https://issues.apache.org/jira/browse/FELIX-2099) : Cleanup logging
- [FELIX-2125](https://issues.apache.org/jira/browse/FELIX-2125) : Localization of the bundle headers &amp; bundle name
- [FELIX-2146](https://issues.apache.org/jira/browse/FELIX-2146) : Tab sorting and access of Configuration Status page is unstable
- [FELIX-2148](https://issues.apache.org/jira/browse/FELIX-2148) : Support selection of default ConfigurationPrinter to display by URL
- [FELIX-2158](https://issues.apache.org/jira/browse/FELIX-2158) : Localization of plugin titles
- [FELIX-2162](https://issues.apache.org/jira/browse/FELIX-2162) : The bundle repository page can't scale
- [FELIX-2167](https://issues.apache.org/jira/browse/FELIX-2167) : Simplify UpdateHelper.updateFromBundleLocation method
- [FELIX-2171](https://issues.apache.org/jira/browse/FELIX-2171) : The OBR page should be able to display detailed information about a bundle
- [FELIX-2189](https://issues.apache.org/jira/browse/FELIX-2189) : Shell Plugin Should Only be Available if shell is available
- [FELIX-2199](https://issues.apache.org/jira/browse/FELIX-2199) : Extract Configuration Printers as top-level classes.
- [FELIX-2203](https://issues.apache.org/jira/browse/FELIX-2203) : provide localization of plugin titles
- [FELIX-2218](https://issues.apache.org/jira/browse/FELIX-2218) : webconsole dialog font size
- [FELIX-2226](https://issues.apache.org/jira/browse/FELIX-2226) : Support direct details view of a resource by symbolic name and version
- [FELIX-2227](https://issues.apache.org/jira/browse/FELIX-2227) : Request to OBR plugin should just act as if list=a parameter is set
- [FELIX-2228](https://issues.apache.org/jira/browse/FELIX-2228) : Encoding issues with search queries in the OBR plugin
- [FELIX-2229](https://issues.apache.org/jira/browse/FELIX-2229) : Provide German Translation for the Web Console
- [FELIX-2238](https://issues.apache.org/jira/browse/FELIX-2238) : Bring back icons for backwards compatibility

#### New Feature

- [FELIX-1441](https://issues.apache.org/jira/browse/FELIX-1441) : Search manifest entries of bundles
- [FELIX-1959](https://issues.apache.org/jira/browse/FELIX-1959) : Move towards unified L&amp;F and extended branding support

#### Task

- [FELIX-1988](https://issues.apache.org/jira/browse/FELIX-1988) : Integrate jQuery UI integration into the Web Console Trunk
- [FELIX-2098](https://issues.apache.org/jira/browse/FELIX-2098) : Removed unused AssemblyListRender
- [FELIX-2165](https://issues.apache.org/jira/browse/FELIX-2165) : Remove deprecated Action interface
- [FELIX-2217](https://issues.apache.org/jira/browse/FELIX-2217) : Web Console OBR plugin should work with old OBR and new bundlerepository API
- [FELIX-2220](https://issues.apache.org/jira/browse/FELIX-2220) : Use Manifest Header parser from new utils bundle


### Changes in 2.0.6 (21/Jan/10)

#### Bug

- [FELIX-1961](https://issues.apache.org/jira/browse/FELIX-1961) : NPE when invoking install/update panel
- [FELIX-1983](https://issues.apache.org/jira/browse/FELIX-1983) : WebConsole HttpContext should flush response after setting response status

#### Improvement

- [FELIX-1976](https://issues.apache.org/jira/browse/FELIX-1976) : Define Web Console build without embedded libraries
- [FELIX-1977](https://issues.apache.org/jira/browse/FELIX-1977) : Improve message if a plugin cannot be installed

#### New Feature

- [FELIX-1957](https://issues.apache.org/jira/browse/FELIX-1957) : Make Web Console compatible with OSGi/Minimum-1.0 EE


### Changes in 2.0.4 (21/Dec/09)

#### Bug

- [FELIX-1800](https://issues.apache.org/jira/browse/FELIX-1800) : Bound configurations for which there exists no bundle cannot be edited
- [FELIX-1912](https://issues.apache.org/jira/browse/FELIX-1912) : Bundles without categories are never displayed in the bundle repository list
- [FELIX-1930](https://issues.apache.org/jira/browse/FELIX-1930) : Clicking action on bundles detail page shows full bundle list while URL stays on bundles/&lt;id&gt;

#### Improvement

- [FELIX-1894](https://issues.apache.org/jira/browse/FELIX-1894) : Show more fragment information in bundle details
- [FELIX-1895](https://issues.apache.org/jira/browse/FELIX-1895) : Show configuration property names in configuration forms
- [FELIX-1916](https://issues.apache.org/jira/browse/FELIX-1916) : Rename &quot;Location&quot; label to &quot;Bundle Location&quot; in the bundle details display
- [FELIX-1931](https://issues.apache.org/jira/browse/FELIX-1931) : Keep sort order of bundle list across page reloads (eg. in a cookie)

#### New Feature

- [FELIX-1808](https://issues.apache.org/jira/browse/FELIX-1808) : Support unbinding configurations through the Web Console
- [FELIX-1884](https://issues.apache.org/jira/browse/FELIX-1884) : WebConsole should have a Services plugin


### Changes in 2.0.2 (30/Oct/09)

#### Bug

- [FELIX-1370](https://issues.apache.org/jira/browse/FELIX-1370) : Sometimes the configuration for org.apache.felix.webconsole.internal.servlet.OsgiManager is ignored
- [FELIX-1674](https://issues.apache.org/jira/browse/FELIX-1674) : typo in scr and webconsole - &quot;unsatisifed&quot;


### Changes in 2.0.0 (01/Oct/09)

#### Task

- [FELIX-1014](https://issues.apache.org/jira/browse/FELIX-1014) : Hardcoded list of webconsole plugins in OSGiManager
- [FELIX-1015](https://issues.apache.org/jira/browse/FELIX-1015) : Hardcoded HTML Header/Footer in AbstractWebConsolePlugin
- [FELIX-1043](https://issues.apache.org/jira/browse/FELIX-1043) : Support WebConsole plugins without requiring extending the AbstractWebConsolePlugin
- [FELIX-1211](https://issues.apache.org/jira/browse/FELIX-1211) : How to provide resources like CSS or JavaScript files for plugins
- [FELIX-1281](https://issues.apache.org/jira/browse/FELIX-1281) : Provide official constants of web console request attributes
- [FELIX-1599](https://issues.apache.org/jira/browse/FELIX-1599) : Validate TabWorld license
- [FELIX-1013](https://issues.apache.org/jira/browse/FELIX-1013) : Improve console extensibility
- [FELIX-1607](https://issues.apache.org/jira/browse/FELIX-1607) : Enhance the Web Console Event Plugin

#### Bug

- [FELIX-1020](https://issues.apache.org/jira/browse/FELIX-1020) : Footer redered before content in bundle plugin
- [FELIX-1160](https://issues.apache.org/jira/browse/FELIX-1160) : WebConsole Manifest.MF should specify required version for Servlet API (2.4)
- [FELIX-1164](https://issues.apache.org/jira/browse/FELIX-1164) : Updating a configuration containing a property configured with unbound array size
- [FELIX-1224](https://issues.apache.org/jira/browse/FELIX-1224) : Component display depends on ManagedService/Factory instances to be registered for components
- [FELIX-1230](https://issues.apache.org/jira/browse/FELIX-1230) : Configuration Page depends on ManagedService/Factory instances to be registered for components
- [FELIX-1270](https://issues.apache.org/jira/browse/FELIX-1270) : Displaying the bundle detail view resolves a bundle
- [FELIX-1275](https://issues.apache.org/jira/browse/FELIX-1275) : On the Bundles page, when bundle details are displayed inline, the links to imported and importing bundles are invalid.
- [FELIX-1389](https://issues.apache.org/jira/browse/FELIX-1389) : Main div is not closed
- [FELIX-1415](https://issues.apache.org/jira/browse/FELIX-1415) : &quot;Reload&quot; button in webconsole bundles list doesn't work
- [FELIX-1460](https://issues.apache.org/jira/browse/FELIX-1460) : Can't view installed but unresolved bundle details page
- [FELIX-1622](https://issues.apache.org/jira/browse/FELIX-1622) : NullPointerException
- [FELIX-1623](https://issues.apache.org/jira/browse/FELIX-1623) : Configuration status tabs not correctly rendered in FireFox 3.5
- [FELIX-1630](https://issues.apache.org/jira/browse/FELIX-1630) : Make WebConsole more independent
- [FELIX-1632](https://issues.apache.org/jira/browse/FELIX-1632) : Remove reference to KXml from NOTICE and LICENSE and update OSGi copyright years
- [FELIX-1636](https://issues.apache.org/jira/browse/FELIX-1636) : Html footer and header are always added to the response for a servlet plugin

#### Improvement

- [FELIX-1171](https://issues.apache.org/jira/browse/FELIX-1171) : Enhance Configuration Status Page
- [FELIX-1191](https://issues.apache.org/jira/browse/FELIX-1191) : Add logging to OBR support plugin
- [FELIX-1215](https://issues.apache.org/jira/browse/FELIX-1215) : Provide hyperlinks when referring to bundles
- [FELIX-1217](https://issues.apache.org/jira/browse/FELIX-1217) : Move install/update to separate page
- [FELIX-1221](https://issues.apache.org/jira/browse/FELIX-1221) : Display the alias ID created by Karaf Features when showing service details
- [FELIX-1282](https://issues.apache.org/jira/browse/FELIX-1282) : Cleanup bundle: do not export SCR API, only embedd header parser
- [FELIX-1283](https://issues.apache.org/jira/browse/FELIX-1283) : Order page titles in top navigation ignoring case
- [FELIX-1569](https://issues.apache.org/jira/browse/FELIX-1569) : Remove deprecated Render interface
- [FELIX-1637](https://issues.apache.org/jira/browse/FELIX-1637) : Support additional CSS references provided by plugins

#### New Feature

- [FELIX-1644](https://issues.apache.org/jira/browse/FELIX-1644) : Reintroduce button to update a single bundle


### Changes in 1.2.10 (15/May/09)

#### Bug

- [FELIX-1003](https://issues.apache.org/jira/browse/FELIX-1003) : Saving Apache Felix OSGI Management Console on Safari cause Error 404
- [FELIX-1028](https://issues.apache.org/jira/browse/FELIX-1028) : NPE in configuration view when running webconsole with Equinox
- [FELIX-1048](https://issues.apache.org/jira/browse/FELIX-1048) : Handle fragment bundles differently than &quot;normal&quot; bundles
- [FELIX-1061](https://issues.apache.org/jira/browse/FELIX-1061) : webconsole silently ignores bundles which have no Bundle-SymbolicName header

#### Improvement

- [FELIX-1042](https://issues.apache.org/jira/browse/FELIX-1042) : Add log service to web console
- [FELIX-1049](https://issues.apache.org/jira/browse/FELIX-1049) : Display symbolic name and version in bundle list
- [FELIX-1050](https://issues.apache.org/jira/browse/FELIX-1050) : Display complete manifest entry
- [FELIX-1139](https://issues.apache.org/jira/browse/FELIX-1139) : Remove fixed width of layout
- [FELIX-1143](https://issues.apache.org/jira/browse/FELIX-1143) : Upgrade to Felix Parent POM 1.2.0


### Changes in 1.2.8 (24/Mar/09)

#### Bug

- [FELIX-871](https://issues.apache.org/jira/browse/FELIX-871) : Bundle Repository page displays NullPointerException if no RepositoryAdmin service is available
- [FELIX-873](https://issues.apache.org/jira/browse/FELIX-873) : Server Stop should be Framework stop and does not work correctly
- [FELIX-874](https://issues.apache.org/jira/browse/FELIX-874) : Bundle startlevel of new bundle cannot be set from the bundle installation form
- [FELIX-885](https://issues.apache.org/jira/browse/FELIX-885) : Saving a factory configuraiton instance creates a new instance instead of updating the old instance
- [FELIX-913](https://issues.apache.org/jira/browse/FELIX-913) : IllegalStateException thrown on startup due to OsgiManager trying to unregister a not yet registered resource
- [FELIX-916](https://issues.apache.org/jira/browse/FELIX-916) : Web Console does not start if the org.osgi.service.log package is missing
- [FELIX-918](https://issues.apache.org/jira/browse/FELIX-918) : Relative redirect on config page does not work in WebSphere
- [FELIX-975](https://issues.apache.org/jira/browse/FELIX-975) : Several UI Problems with IE

#### Improvement

- [FELIX-564](https://issues.apache.org/jira/browse/FELIX-564) : Allow changing the sort order in the bundle list
- [FELIX-858](https://issues.apache.org/jira/browse/FELIX-858) : Use new layout from event plugin in bundle plugin
- [FELIX-863](https://issues.apache.org/jira/browse/FELIX-863) : Merge license and NOTICE information of OBR bundle embedded in web console with the main license and NOTICE files
- [FELIX-875](https://issues.apache.org/jira/browse/FELIX-875) : Support updating the system bundle
- [FELIX-882](https://issues.apache.org/jira/browse/FELIX-882) : Use Logger to log messages in the OsgiManager instead of the servlet context log
- [FELIX-888](https://issues.apache.org/jira/browse/FELIX-888) : JSON information and actions for a bundle should be possible with symbolic name
- [FELIX-904](https://issues.apache.org/jira/browse/FELIX-904) : Dependencies should be included as jars and not as classes
- [FELIX-919](https://issues.apache.org/jira/browse/FELIX-919) : Use new table layout for the components list
- [FELIX-933](https://issues.apache.org/jira/browse/FELIX-933) : Config Manager Plugin should support displaying a form even if no configuration is stored
- [FELIX-955](https://issues.apache.org/jira/browse/FELIX-955) : Cleanup Bundle Repository Page
- [FELIX-956](https://issues.apache.org/jira/browse/FELIX-956) : Enhance log output in case of failed resource resolution

#### New Feature

- [FELIX-878](https://issues.apache.org/jira/browse/FELIX-878) : Allow to get configurations directly in json format


### Changes in 1.2.2 (29/Dec/08)

#### Bug

- [FELIX-738](https://issues.apache.org/jira/browse/FELIX-738) : First access to &quot;Bundles&quot; web console tab takes a long time if the server has no internet access 
- [FELIX-752](https://issues.apache.org/jira/browse/FELIX-752) : webconsole 1.2.0 seems to depend on SCR
- [FELIX-767](https://issues.apache.org/jira/browse/FELIX-767) : NOTICE file should list OSGi under &quot;includes&quot; and &quot;uses&quot;
- [FELIX-774](https://issues.apache.org/jira/browse/FELIX-774) : Checkboxes do not work in configuration admin
- [FELIX-780](https://issues.apache.org/jira/browse/FELIX-780) : ArrayIndexOutOfBoundsException in webconsole
- [FELIX-802](https://issues.apache.org/jira/browse/FELIX-802) : Bundle and other displays incomplete and show JavaScript error(s)
- [FELIX-856](https://issues.apache.org/jira/browse/FELIX-856) : Web Console fails to start if HttpService is only available after the WebConsole bundle start
- [FELIX-859](https://issues.apache.org/jira/browse/FELIX-859) : Event display shows double lines between event properties for some events

#### Improvement

- [FELIX-757](https://issues.apache.org/jira/browse/FELIX-757) : Add status message about bundle
- [FELIX-793](https://issues.apache.org/jira/browse/FELIX-793) : Improve update and install through web console
- [FELIX-857](https://issues.apache.org/jira/browse/FELIX-857) : Do not rely on Declarative Services for plugins of the Web Console itself

#### New Feature

- [FELIX-781](https://issues.apache.org/jira/browse/FELIX-781) : Add basic thread dump to Configuration Status page
- [FELIX-790](https://issues.apache.org/jira/browse/FELIX-790) : Add console plugin to display OSGi events


### Changes in 1.2.0 (14/Oct/08)

#### Bug

- [FELIX-563](https://issues.apache.org/jira/browse/FELIX-563) : Add support to access the Felix ShellService
- [FELIX-583](https://issues.apache.org/jira/browse/FELIX-583) : org.apache.felix.webconsole.internal.compendium.ConfigManager.listConfigurations(): ManagedServiceFactory instances are listed twice: with pid and factoryPid
- [FELIX-584](https://issues.apache.org/jira/browse/FELIX-584) : org.apache.felix.webconsole.internal.compendium.AjaxConfigManagerAction.applyConfiguration(): &quot;create&quot; action is not handled properly
- [FELIX-585](https://issues.apache.org/jira/browse/FELIX-585) : org.apache.felix.webconsole.internal.compendium.ConfigManager.listConfigurations(): Configuration instances for ManagedServiceFactoy instances cause IllegalArgumentException
- [FELIX-586](https://issues.apache.org/jira/browse/FELIX-586) : org.apache.felix.webconsole.internal.compendium.BaseConfigManager.getAttributeDefinitionMap(): implementation does not properly handle Configuration instances of a ManagedServiceFactory
- [FELIX-587](https://issues.apache.org/jira/browse/FELIX-587) : org.apache.felix.webconsole.internal.compendium.AjaxConfigManagerAction.configForm(): Configuration instance for a ManagedServiceFactory will cause Exception
- [FELIX-592](https://issues.apache.org/jira/browse/FELIX-592) : Console does not work properly in Internet Explorer
- [FELIX-600](https://issues.apache.org/jira/browse/FELIX-600) : Insert Delay before refreshing packages after install/update
- [FELIX-662](https://issues.apache.org/jira/browse/FELIX-662) : ConfigManager should check for empty value before converting into a specific type

#### Improvement

- [FELIX-566](https://issues.apache.org/jira/browse/FELIX-566) : More RESTful management console URLs
- [FELIX-574](https://issues.apache.org/jira/browse/FELIX-574) : Replace Action and Render service interfaces by the Servlet interface
- [FELIX-614](https://issues.apache.org/jira/browse/FELIX-614) : Change behaviour of little arrow on single bundle/component display
- [FELIX-671](https://issues.apache.org/jira/browse/FELIX-671) : Web Console OBR description
- [FELIX-742](https://issues.apache.org/jira/browse/FELIX-742) : Order configurations alphabetically
- [FELIX-743](https://issues.apache.org/jira/browse/FELIX-743) : Support configuration filtering
- [FELIX-744](https://issues.apache.org/jira/browse/FELIX-744) : Support configuration creation from the GET request
- [FELIX-745](https://issues.apache.org/jira/browse/FELIX-745) : Mark optional imports as such
- [FELIX-746](https://issues.apache.org/jira/browse/FELIX-746) : Display Bundle Doc URL as a link
- [FELIX-747](https://issues.apache.org/jira/browse/FELIX-747) : Enable bundle installation through OBR again

#### New Feature

- [FELIX-604](https://issues.apache.org/jira/browse/FELIX-604) : Add License/Notice page
- [FELIX-691](https://issues.apache.org/jira/browse/FELIX-691) : Add support for the deployment admin


### Initial Release 1.0.0 (26/May/08)

#### Improvement

- [FELIX-570](https://issues.apache.org/jira/browse/FELIX-570) : Add flag to InstallAction asking for PackageAdmin.refreshPackages after package update

#### Task

- [FELIX-562](https://issues.apache.org/jira/browse/FELIX-562) : Move OSGi Console to Apache Felix
