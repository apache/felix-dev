# Web Console RESTful API

WARNING: Please note that the APIs described on this page have not been standardized or stabilized yet.
As such they cannot be relied upon.
They are merely a description of the current code.
In future releases these APIs will be fixed to be properly RESTful.

## URL Space

The Web Console URL space is by default rooted at `/system/console` inside the Servlet Context used by the web console. This default can be reconfigured, though this is not really recommended.

It is expected that the Web Console owns the complete URL space below the root path.

The next level in the URL Space of the Web Console is defined by the labels defined by the registered Web Console plugins. For example the Bundles plugins registers itself with the `bundles` label.

## Request Methods

Although the HTTP RFC defines a number of request methods and the REST architectural style mandates to use these methods as intended by the specification, Web Browsers in general only support the GET and POST methods.
Moreover, firewall administrators more often than not block all HTTP methods except GET and POST.
For this reason the Web Console limits the used methods to just these two.

## Bundles Plugin

### URLs

`.../bundles` : addresses all bundles in the framework

`.../bundles/*id*` : addresses a single bundle in the framework.
The `*id*` can in this case be the bundle's ID (as per `Bundle.getBundleId()`), the bundle's symbolic name (which actually may cause multiple bundles to be addressed), or the bundle's symbolic name and version separated by a colon.
Examples are `.../bundles/0`, `.../bundles/org.apache.felix.webconsole`, and `.../bundles/org.apache.felix.webconsole:5.0.0`.

### GET Requests

GET requests are used to retrieve information on the bundles (or bundle) available in the framework.
To response formats are currently supported: regular HTML and JSON if requests are sent with the `.json` extension;
e.g.
`.../bundles/0.json`.
The HTML response is destined at browsers and is not further described here.

### GET .../bundles.json

Requests to this URL send back an overview of all bundles along with an overview of all bundles (bundles existing, active, fragment, resolved, installed)

```json
{
    "status": "Bundle information: 84 bundles in total - all 84 bundles active.",
    "s": [ 84, 81, 3, 0, 0 ],
    "data": [
       {
          "id": 0,
          "name": "System Bundle",
          "fragment": false,
          "stateRaw": 32,
          "state": "Active",
          "version": "3.0.8",
          "symbolicName": "org.apache.felix.framework",
          "category": ""
       },
       ....
    ]
}
```

### GET .../bundles/id.json

This URL returns detailed information of a bundle like the bundle headers, imported packages, exported packages, importing bundles, registered services.

```json
{
     "status": "Bundle information: 84 bundles in total - all 84 bundles active.",
     "s": [
         84,
         81,
         3,
         0,
         0
     ],
     "data": [
         {
             "id": 23,
             "name": "Apache Felix Web Management Console",
             "fragment": false,
             "stateRaw": 32,
             "state": "Active",
             "version": "3.1.9.SNAPSHOT",
             "symbolicName": "org.apache.felix.webconsole",
             "category": "",
             "props": [
                 {
                     "key": "Symbolic Name",
                     "value": "org.apache.felix.webconsole"
                 },
                 {
                     "key": "Version",
                     "value": "3.1.9.SNAPSHOT"
                 },
                 {
                     "key": "Bundle Location",
                     "value": "slinginstall:org.apache.felix.webconsole-3.1.6.jar"
                 },
                 {
                     "key": "Last Modification",
                     "value": "Sun Sep 25 20:59:46 CEST 2011"
                 },
                 {
                     "key": "Bundle Documentation",
                     "value": "http://felix.apache.org/site/apache-felix-web-console.html"
                 },
                 {
                     "key": "Vendor",
                     "value": "The Apache Software Foundation"
                 },
                 {
                     "key": "Description",
                     "value": "Web Based Management Console for OSGi Frameworks. See http://felix.apache.org/site/apache-felix-web-console.html for more information on this bundle."
                 },
                 {
                     "key": "Start Level",
                     "value": 5
                 },
                 {
                     "key": "Exported Packages",
                     "value": [
                         "org.apache.felix.webconsole,version=3.1.2"
                     ]
                 },
                 {
                     "key": "Imported Packages",
                     "value": [
                         "javax.servlet,version=2.5.0 from <a href='/system/console/bundles/15'>org.apache.felix.http.jetty (15)</a>",
                         "javax.servlet.http,version=2.5.0 from <a href='/system/console/bundles/15'>org.apache.felix.http.jetty (15)</a>",
                         "org.apache.felix.scr,version=1.6.0 from <a href='/system/console/bundles/11'>org.apache.felix.scr (11)</a>",
                         "org.osgi.framework,version=1.5.0 from <a href='/system/console/bundles/0'>org.apache.felix.framework (0)</a>",
                         "org.osgi.service.cm,version=1.3.0 from <a href='/system/console/bundles/9'>org.apache.felix.configadmin (9)</a>",
                         "org.osgi.service.http,version=1.2.0 from <a href='/system/console/bundles/15'>org.apache.felix.http.jetty (15)</a>",
                         "org.osgi.service.log,version=1.3.0 from <a href='/system/console/bundles/6'>org.apache.sling.commons.logservice (6)</a>",
                         "org.osgi.service.metatype,version=1.1.0 from <a href='/system/console/bundles/12'>org.apache.felix.metatype (12)</a>",
                         "org.osgi.service.packageadmin,version=1.2.0 from <a href='/system/console/bundles/0'>org.apache.felix.framework (0)</a>",
                         "org.osgi.service.startlevel,version=1.1.0 from <a href='/system/console/bundles/0'>org.apache.felix.framework (0)</a>"
                     ]
                 },
                 {
                     "key": "Importing Bundles",
                     "value": [
                         "<a href='/system/console/bundles/19'>org.apache.felix.webconsole.plugins.memoryusage (19)</a>",
                         "<a href='/system/console/bundles/62'>org.apache.sling.commons.mime (62)</a>",
                         "<a href='/system/console/bundles/14'>org.apache.sling.extensions.threaddump (14)</a>",
                         "<a href='/system/console/bundles/20'>org.apache.sling.extensions.webconsolesecurityprovider (20)</a>",
                         "<a href='/system/console/bundles/18'>org.apache.sling.jcr.webconsole (18)</a>"
                     ]
                 },
                 {
                     "key": "Service ID <a href='/system/console/services/369'>369</a>",
                     "value": [
                         "Types: org.apache.felix.webconsole.ConfigurationPrinter"
                     ]
                 },
                 {
                     "key": "Service ID <a href='/system/console/services/370'>370</a>",
                     "value": [
                         "Types: org.apache.felix.webconsole.ConfigurationPrinter"
                     ]
                 },
                 {
                     "key": "Service ID <a href='/system/console/services/371'>371</a>",
                     "value": [
                         "Types: org.apache.felix.webconsole.ConfigurationPrinter"
                     ]
                 },
                 {
                     "key": "Service ID <a href='/system/console/services/372'>372</a>",
                     "value": [
                         "Types: org.apache.felix.webconsole.ConfigurationPrinter"
                     ]
                 },
                 {
                     "key": "Service ID <a href='/system/console/services/373'>373</a>",
                     "value": [
                         "Types: org.apache.felix.webconsole.ConfigurationPrinter"
                     ]
                 },
                 {
                     "key": "Service ID <a href='/system/console/services/374'>374</a>",
                     "value": [
                         "Types: org.apache.felix.webconsole.ConfigurationPrinter"
                     ]
                 },
                 {
                     "key": "Service ID <a href='/system/console/services/375'>375</a>",
                     "value": [
                         "Types: org.apache.felix.webconsole.ConfigurationPrinter"
                     ]
                 },
                 {
                     "key": "Service ID <a href='/system/console/services/376'>376</a>",
                     "value": [
                         "Types: org.osgi.service.cm.ManagedService, org.osgi.service.metatype.MetaTypeProvider",
                         "Service PID: org.apache.felix.webconsole.internal.servlet.OsgiManager",
                         "Description: OSGi Management Console Configuration Receiver",
                         "Vendor: The Apache Software Foundation"
                     ]
                 },
                 {
                     "key": "Service ID <a href='/system/console/services/453'>453</a>",
                     "value": [
                         "Types: org.apache.felix.webconsole.ConfigurationPrinter"
                     ]
                 },
                 {
                     "key": "Manifest Headers",
                     "value": [
                         "Bnd-LastModified: 1316977184980",
                         "Build-Jdk: 1.6.0_13",
                         "Built-By: fmeschbe",
                         "Bundle-Activator: org.apache.felix.webconsole.internal.OsgiManagerActivator",
                         "Bundle-Description: Web Based Management Console for OSGi Frameworks. See http://felix.apache.org/site/apache-felix-web-console.html for more information on this bundle.",
                         "Bundle-DocURL: http://felix.apache.org/site/apache-felix-web-console.html",
                         "Bundle-License: http://www.apache.org/licenses/LICENSE-2.0.txt",
                         "Bundle-ManifestVersion: 2",
                         "Bundle-Name: Apache Felix Web Management Console",
                         "Bundle-SymbolicName: org.apache.felix.webconsole",
                         "Bundle-Vendor: The Apache Software Foundation",
                         "Bundle-Version: 3.1.9.SNAPSHOT",
                         "Created-By: Apache Maven Bundle Plugin",
                         "DynamicImport-Package: org.apache.felix.bundlerepository, org.osgi.service.obr",
                         "Export-Package: org.apache.felix.webconsole; uses:=\"javax.servlet, org.osgi.framework, javax.servlet.http\"; version=\"3.1.2\"",
                         "Import-Package: javax.servlet; version=\"2.4\", javax.servlet.http; version=\"2.4\", org.apache.felix.scr; resolution:=optional; version=\"1.0\", org.apache.felix.shell; resolution:=optional, org.apache.felix.webconsole; version=\"3.1.2\", org.osgi.framework, org.osgi.service.cm; resolution:=optional, org.osgi.service.condpermadmin; resolution:=optional, org.osgi.service.deploymentadmin; resolution:=optional, org.osgi.service.http, org.osgi.service.log; resolution:=optional, org.osgi.service.metatype; resolution:=optional, org.osgi.service.packageadmin; resolution:=optional, org.osgi.service.permissionadmin; resolution:=optional, org.osgi.service.prefs; resolution:=optional, org.osgi.service.startlevel; resolution:=optional, org.osgi.service.wireadmin; resolution:=optional",
                         "Manifest-Version: 1.0",
                         "Tool: Bnd-0.0.255"
                     ]
                 }
             ]
         }
     ]
 }
```

### POST Requests

To update the bundles the `action` request parameter is used to indicate the action:

`install` : Installs (or updates) and optionally starts one or more bundles.
Parameters:

* `bundlestart` -- whether to start newly installed bundles or not.
Has no influence on updated bundles.
* `bundlestartlevel` -- the start level to set on newly installed bundles.
Has no influence on updated bundles.
* `bundlefile` -- one or more uploaded files being the bundles to install or update.
The manifests in the bundles are inspected to see whether any bundle is an update or new install.
* `refreshPackages` -- whether to call `PackageAdmin.refreshPackages(Bundle[])` with the installed/updated bundles after installation/update.

`start` : Starts the bundle addressed by the request URL.

`stop` : Stops the bundle addressed by the request URL.

`refresh` : Calls `PackageAdmin.refreshPackages(Bundle[])` with the bundle as its sole argument thus forcing the bundle to be rewired. The bundle is required to be addressed by the request URL.

`update` : Calls `Bundle.update()` on the bundle addressed by the request URL or tries to update the bundle through the OBR.

`uninstall` : Calls `Bundle.uninstall()` on the bundle addressed by the request URL. After the installation the framework must be refreshed (see `refreshPackages` above).

`refreshPackages` : Calls `PackageAdmin.refreshPackages(Bundle[])` with a `null` argument thus refreshing all pending bundles.
This action does not require a bundle in the URL and just ignores if one is provided.

The response on those actions requiring a bundle is a simple JSON response:

```json
{
     "fragment": -- whether the bundle is a fragement
     "stateRaw": -- the state code of the bundle after executing the action
}
```

Since some bundle operations take place asynchronously a short delay of 800ms is inserted before preparing and sending the response.

The response on those actions not taking a bundle is the bundle overview of the bundles in the framework as if requesting `.../bundles.json`.
Again a delay of 800ms is inserted since some operations are executed asynchronously.

## Services Plugin

TBD

## Configuration Admin Plugin

The Configuration Admin Plugin can be accessed directly by sending POST requests to it.

### POST Requests

Configuration handling is done based on the PID of the configuration.
Each POST can either contain the PID as a suffix like `../PID` or with the parameter `pid`.
The parameter `pidFilter` might contain an additional filter expression.
For the action to execute, the following options are tested, one after the other.
As soon as one is executed, the request is processed.

#### Create

If the parameter ``create``is sent, a new configuration with the PID is created.
The value of the parameter is not evaluated.

#### Apply

If the parameter `apply` is sent, the configuration is changed.
The value of the parameter is not evaluated.
The parameter `factoryPid` might contain the factory pid.
The parameter `propertyList` contains a comma-separated list of all configuration property names that will be changed by this POST.
For each name, the value of the corresponding request parameter is used to set the value.
If such a parameter is missing, the property is not changed.
Any existing property not listed in the property list will be removed from the configuration.

For example to use `curl` to apply a configuration the following command line can be used:

> curl -u admin:admin -X POST -d "apply=true" -d "propertylist=foo,bar" -d "foo=51" -d "bar=hello" http://localhost:8080/system/console/configMgr/com.acme.MyPid

If the configuration contains property where the names clash with the commands of the rest api like `apply` or `propertyList` the request parameter name must be prefixed with a dollar sign:

> curl -u admin:admin -X POST -d "apply=true" -d "propertylist=update" -d "$update=yes" http://localhost:8080/system/console/configMgr/com.acme.mypid

To create a factory configuration, the special PID `[Temporary PID replaced by real PID upon save]` must be used, URL encoded.
So to create a new factory configuration  for a factoryPid `com.acme.MyFactoryPid` the following can be used:

> curl -u admin:admin -X POST -d "apply=true" -d "propertylist=name" -d "name=mycfg" -d "factoryPid=com.acme.MyFactoryPid" http://localhost:8080/system/console/configMgr/%5BTemporary%20PID%20replaced%20by%20real%20PID%20upon%20save%5D

#### Delete

If the parameters `apply` and `delete` are sent, the configuration is removed.
The values of the parameters is not evaluated.

Example using `curl`:

> curl -u admin:admin  -X POST -d "apply=true" -d "delete=true" http://localhost:8080/system/console/configMgr/com.acme.MyPid

#### Unbind

If the parameter `unbind` is sent, the configuration is unbind.
