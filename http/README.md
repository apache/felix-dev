# Apache Felix HTTP Service

This is an implementation of the [R8.1 Whiteboard Specification for Jakarta Servlet](https://docs.osgi.org/specification/osgi.cmpn/8.1.0/service.servlet.html), [HTTP Whiteboard Service as described in chapter 140](https://osgi.org/specification/osgi.cmpn/7.0.0/service.http.whiteboard.html) of the OSGi Compendium (R7) in combination with an implementation of the [HTTP Service Specification as described in chapter 102](https://osgi.org/specification/osgi.cmpn/7.0.0/service.http.html) of the OSGi Compendium. The goal is to provide a standard and simplified way to register servlets, listeners, filters, and resources in a servlet container, to managed them in servlet contexts, and to associate them with URIs. Complete set of features:

  * Standard OSGi Http Service implementation
  * Standard OSGi Http Whiteboard implementation
  * Run either with Jetty (version 11 or 12) bundle or inside your own application server using the servlet bridge
    * [Felix HTTP Jetty 12](https://mvnrepository.com/artifact/org.apache.felix/org.apache.felix.http.jetty12) is the preferred bundle of choice as it supports JakartaEE10 with the `jakarta` namespace.
      * [Jetty WebSocket support](https://github.com/apache/felix-dev/pull/310), see example code [here](https://github.com/apache/felix-dev/blob/master/http/samples/whiteboard/src/main/java/org/apache/felix/http/samples/whiteboard/TestWebSocketServlet.java).
    * [Felix HTTP Jetty 11](https://mvnrepository.com/artifact/org.apache.felix/org.apache.felix.http.jetty) is the predecessor of the Jetty 12 bundle, which shipped with [Jetty 9.4.x](https://mvnrepository.com/artifact/org.apache.felix/org.apache.felix.http.jetty/4.2.26) in the 4.x range (JavaEE8), [Jetty 11.x](https://mvnrepository.com/artifact/org.apache.felix/org.apache.felix.http.jetty/5.1.10) in the 5.x range (JakartaEE9).
      * [Jetty WebSocket support](https://github.com/apache/felix-dev/pull/309), see example code [here](https://github.com/apache/felix-dev/blob/master/http/samples/whiteboard/src/main/java/org/apache/felix/http/samples/whiteboard/TestWebSocketServlet.java).
  * Correctly versioned Servlet API.

## Installing 

The Apache Felix HTTP Service project includes several bundles.

  * [`org.apache.felix.http.servlet-api`](https://mvnrepository.com/artifact/org.apache.felix/org.apache.felix.http.servlet-api) - Provides the Servlet API (versions 2.6, 3.0, 3.1, 4.0, 5.0 and 6.0 of the Servlet specification)
  * [`org.apache.felix.http.api`](https://mvnrepository.com/artifact/org.apache.felix/org.apache.felix.http.api) - Provides the OSGi APIs for the Http Whiteboard and Http Service.
  * [`org.apache.felix.http.jetty12`](https://mvnrepository.com/artifact/org.apache.felix/org.apache.felix.http.jetty12) - Implementation that is embedding Jetty server (currently Jetty 12.x, requiring Java 17). This bundle includes the http.api bundle. It's the the preferred Felix Jetty bundle to use, as Jetty 11 will be [EoL in 2025](https://github.com/jetty/jetty.project/issues/10485).
  * [`org.apache.felix.http.jetty`](https://mvnrepository.com/artifact/org.apache.felix/org.apache.felix.http.jetty) - Predecessor implementation that is embedding Jetty server (currently Jetty 11.x, requiring Java 11). This bundle includes the http.api bundle. 
  * [`org.apache.felix.http.sslfilter`](https://mvnrepository.com/artifact/org.apache.felix/org.apache.felix.http.sslfilter) - Servlet filter for handling SSL termination.
  * [`org.apache.felix.http.bridge`](https://mvnrepository.com/artifact/org.apache.felix/org.apache.felix.http.bridge) - Implementation that uses the host application server (bridged mode). Must be used with the proxy (see below)
  * [`org.apache.felix.http.proxy`](https://mvnrepository.com/artifact/org.apache.felix/org.apache.felix.http.proxy) - Proxy that is needed inside WAR when deployed inside an application server.
  * [`org.apache.felix.http.wrappers`](https://mvnrepository.com/artifact/org.apache.felix/org.apache.felix.http.wrappers) offers wrapper classes that can be used when transitioning to the `jakarta` namespace, while having libraries that don't offer a jakartified version yet. 

Note that as of version **3.x**, the Servlet APIs are **no longer** packaged with the implementation bundles! If you are migrating from lower versions, be sure to add the
`org.apache.felix.http.servlet-api` (or any other compatible Serlvet API bundle) to your
classpath and deployment!

### Using classifiers: `light`, `with-jetty-websockets` and `with-jakarta-websockets` bundle
If you would like to use your own Jetty jars instead of the one packaged with the Felix Jetty bundles, you can use the variants with the following classifiers:
* `light` - A light version of the bundle that does not include the Jetty classes. This is useful when you want to use your own Jetty jars. Available for both Jetty bundles.
* `with-jetty-websockets` - A bundle that includes the classes required for Jetty WebSocket support for Jakarta EE10. Jetty12 bundle only.
* `with-jakarta-websockets` - A bundle that includes the classes required for Jakarta WebSocket support for Jakarta EE10. Jetty12 bundle only.

When building the Felix Jetty bundle with Maven (`mvn clean install`), the additional bundles will be created in the `target` directory, postfixed with classifier.
This jar can be deployed to your Felix OSGi environment, along with a compatible Jetty jars.
See the unit tests for the required bundles and versions that need to be deployed.

Or just use maven to include the dependency with the proper classifier. 
```
<dependency>
   <groupId>org.apache.felix</groupId>
   <artifactId>org.apache.felix.http.jetty12</artifactId>
   <version>1.0.2</version>
   <classifier>light</classifier>
   <scope>runtime</scope>
</dependency>
```

## Installing additional bundles for the optional HTTP/2 support with jetty

Configure `org.apache.felix.http2.enable=true` to enable HTTP/2 in Jetty.

The Jetty implementation uses the OSGi ServiceLoader mediator technique to find certain pluggable components that are required for HTTP/2 support. Your OSGi runtime must first have the bundles needed for OSGi ServiceLoader support deployed. Background information about the OSGi ServiceLoader integration is discussed [here](https://blog.osgi.org/2013/02/javautilserviceloader-in-osgi.html)

Deploying the following set of bundles would be one way to enable the ServiceLoader mediator support:

  * [`org.apache.aries.spifly:org.apache.aries.spifly.dynamic.bundle:1.3.7`](https://mvnrepository.com/artifact/org.apache.aries.spifly/org.apache.aries.spifly.dynamic.bundle)
  * [`org.ow2.asm:asm:9.7`](https://mvnrepository.com/artifact/org.ow2.asm/asm)
  * [`org.ow2.asm:asm-analysis:9.7`](https://mvnrepository.com/artifact/org.ow2.asm/asm-analysis)
  * [`org.ow2.asm:asm-commons:9.7`](https://mvnrepository.com/artifact/org.ow2.asm/asm-commons)
  * [`org.ow2.asm:asm-tree:9.7`](https://mvnrepository.com/artifact/org.ow2.asm/asm-tree)
  * [`org.ow2.asm:asm-util:9.7`](https://mvnrepository.com/artifact/org.ow2.asm/asm-util)

### Jetty 12 bundle
For the Jetty 12 bundle, start the following set of bundles _before_ the Jetty 12 bundle, but after the beforementioned ServiceLoader bundles (the order is important and can be configured in `felix.auto.start.1`). 
The Jetty version should correspond with the version used in the [Jetty 12 bundle](https://github.com/apache/felix-dev/blob/master/http/jetty12/pom.xml#L44).

* [`jetty-alpn-server-${jetty.version}`](https://mvnrepository.com/artifact/org.eclipse.jetty/jetty-alpn-server)
* [`jetty-alpn-java-server-${jetty.version}`](https://mvnrepository.com/artifact/org.eclipse.jetty/jetty-alpn-java-server)

### Jetty 11 bundle

For the predecessor Jetty 11 bundle, depending on your server environment, you must choose only one of the following sets
of additional bundles to deploy [as described in the jetty documentation](https://eclipse.dev/jetty/documentation/jetty-9/index.html#alpn)

1. For java 9 or later:
    * `org.eclipse.jetty.alpn:alpn-api:1.1.3.v20160715`
    * `org.eclipse.jetty:jetty-alpn-java-server:${jetty.version}`

2. For java 8:
    * `org.eclipse.jetty:jetty-alpn-openjdk8-server:${jetty.version}`

    * For java 8 versions earlier than 1.8.0_252 download and add -Xbootclasspath/p:/path_to_file_here/alpn-boot-8.1.13.v20181017.jar as an argument to the java process and then deploy the following bundle:
        * `org.eclipse.jetty.osgi:jetty-osgi-alpn:${jetty.version}`

    * For java 8 version 1.8.0_252 or later skip the bootclasspath argument and deploy the following bundle instead:
        * `org.eclipse.jetty.alpn:alpn-api:1.1.3.v20160715`


## Using the OSGi Http Whiteboard

The OSGi whiteboard implementation simplifies the task of registering servlets, filters, resources, listeners, and servlet contexts. For a complete introduction, please refer to the OSGi R7 Compendium or Enterprise specification.

For a short introduction: Such a whiteboard service can be registered by exporting it as a service, making it no longer necessary to track and use the `HttpService` directly (or the `ExtHttpService` in legacy implementations, for registering filters). The
whiteboard implementation detects all `jakarta.servlet.Servlet` and `jakarta.servlet.Filter` services with the right service properties. Let us illustrate the usage by registering a servlet and a filter:

```java
public class Activator implements BundleActivator {
    private ServiceRegistration<Servlet> servletRegistration;
    private ServiceRegistration<Filter> filterRegistration;

    public void start(BundleContext context) throws Exception {
        Hashtable propsServlet = new Hashtable();
        propsServlet.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, "/hello");
        propsServlet.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_ASYNC_SUPPORTED, true);
        propsServlet.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_INIT_PARAM_PREFIX + "message", "Hello World servlet!");

        this.servletRegistration = context.registerService(Servlet.class.getName(), new HelloWorldServlet(), propsServlet);

        Hashtable propsFilter = new Hashtable();
        propsFilter.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_REGEX, ".*");
        propsFilter.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_ASYNC_SUPPORTED, true);
        propsFilter.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_INIT_PARAM_PREFIX + "message", "Hello World filter!");

        this.filterRegistration = context.registerService(Filter.class.getName(), new HelloWorldFilter(), propsFilter);
    }

    public void stop(BundleContext context) throws Exception {
        this.servletRegistration.unregister();
        this.filterRegistration.unregister();
    }
}
```
An implementation note for when using the Felix HTTP Jetty 12 bundle: only registering a filter without a servlet, will not work. Make sure that there is a servlet registered (on the same path as the filter) when registering a filter, even when that servlet is not hit eventually.

To ensure the HTTP whiteboard service picks up your servlet and filter correctly, your service
registration *must* provide several service properties.


### Servlet service properties

See full reference in the [OSGi specification](https://docs.osgi.org/specification/osgi.cmpn/8.1.0/service.servlet.html#service.servlet-i21223311).
The most important properties are:

  * `osgi.http.whiteboard.servlet.pattern` - defines the servlet pattern to register the servlet under, should be a path as defined in the Servlet specification.
  * `osgi.http.whiteboard.context.select` - Filter expression to select the servlet context (optional).
  * `osgi.http.whiteboard.servlet.asyncSupported` - Declares whether the servlet supports the asynchronous operation mode. Allowed values are true and false independent of case. Defaults to false. (optional)
  * `osgi.http.whiteboard.servlet.multipart.enable` - Enables support for multipart configuration on the servlet. Allowed values are true and false independent of case. Defaults to false. (optional)
  * `servlet.init.*` - these properties (sans the `servlet.init.` prefix) are made available throught the `ServletConfig` object of your servlet. This allows you to supply your servlet initialization parameters as you would normally do in the web descriptor (web.xml).

### Filter service properties

See full reference in the [OSGi specification](https://docs.osgi.org/specification/osgi.cmpn/8.1.0/service.servlet.html#d0e87922) .
The most important properties are:

  * `osgi.http.whiteboard.filter.regex` - The regular expression pattern to register filter with.
  * `osgi.http.whiteboard.context.select` - Filter expression to select the servlet context (optional).
  * `osgi.http.whiteboard.filter.pattern` - Apply this servlet filter to the specified URL path patterns. The format of the patterns is specified in the servlet specification.
  * `osgi.http.whiteboard.filter.asyncSupported` - Declares whether the servlet filter supports asynchronous operation mode. Allowed values are true and false independent of case. Defaults to false. (optional)
  * `filter.init.*` - these properties (sans the `filter.init.` prefix) are made available throught the `FilterConfig` object of your filter. This allows you to supply your filter initialization parameters as you would normally do in the web descriptor (web.xml).

The order of filters is no longer managed by the `service.ranking` property, but by implementing the `compareTo` method. 
Multiple servlet filters can process the same servlet request/response. If more than one Filter matches, they are processed in ranking order, as specified in `ServiceReference.compareTo`. The servlet filter with the highest ranking is processed first in the filter chain, while the servlet filter with the lowest ranking is processed last, before the Servlet.service method is called. After the servlet completes its service method the filter chain is unwound in reverse order.

### ServletContextHelper service properties

See full reference in the [OSGi specification](https://docs.osgi.org/specification/osgi.cmpn/8.1.0/service.servlet.html#service.http.whiteboard.servletcontext). 
The most important properties are:

  * `osgi.http.whiteboard.context.name` - the identifier of the registered HTTP context to be referenced by a servlet or filter service
  * `osgi.http.whiteboard.context.path` - The path of the servlet context.
  * `context.init.*` - Properties starting with this prefix are provided as init parameters through the ServletContext.getInitParameter and ServletContext.getInitParameterNames methods. The context.init. prefix is removed from the parameter name.

## Using the HttpService

In general the Http Service is regarded legacy and the OSGi Http Whiteboard should be used instead. If you still want to use the Http Service, this is a brief introduction. The main components provided by the Apache Felix HTTP Service bundle are:

 * `HttpService` - Service used to dynamically register resources and servlets;
 * `HttpContext` - Additional (optional) component to handle authentication, resource and mime type mappings.

Servlets created for the OSGi HTTP service don't need to have any reference to the OSGi specification (they only need to
conform to the Servlet specification), like in the example:

```java
public class HelloWorld extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.getWriter().write("Hello World");		
    }
}
```

To register a servlet and map it to a URI, you need to retrieve the `HttpService` and call its `registerServlet` method.
For this example, a `ServiceTracker` is used to ensure that the registration occurs when a `HttpService` actually is
available, and a deregistration occurs when the `HttpService` becomes unavailable. Alternatively, you can use more
high-level dependency management libraries, like [Declarative Services](https://github.com/apache/felix-dev/tree/master/scr), [Felix Dependency Manager](https://felix.apache.org/documentation/subprojects/apache-felix-dependency-manager.html), or Felix HTTP
whiteboard service (see below).

```java
public class Activator implements BundleActivator {
    private ServiceTracker httpTracker;

    public void start(BundleContext context) throws Exception {
        httpTracker = new ServiceTracker(context, HttpService.class.getName(), null) {
          public void removedService(ServiceReference reference, Object service) {
            // HTTP service is no longer available, unregister our servlet...
            try {
               ((HttpService) service).unregister("/hello");
            } catch (IllegalArgumentException exception) {
               // Ignore; servlet registration probably failed earlier on...
            }
          }

          public Object addingService(ServiceReference reference) {
            // HTTP service is available, register our servlet...
            HttpService httpService = (HttpService) this.context.getService(reference);
            try {
              httpService.registerServlet("/hello", new HelloWorld(), null, null);
            } catch (Exception exception) {
              exception.printStackTrace();
            }
            return httpService;
          }
        };
        // start tracking all HTTP services...
        httpTracker.open();
    }

    public void stop(BundleContext context) throws Exception {
        // stop tracking all HTTP services...
        httpTracker.close();
    }
}
```

In the same way, you can unregister a servlet (for instance, in the `removedMethod` method in the former example) by
calling the `HttpService.unregister` method.

As you notice in the example above, the `registerServlet` method accepts four parameters:

 * the servlet alias;
 * the `Servlet` instance;
 * an additional configuration `Dictionary`;
 * a `HttpContext`.

The servlet alias must begin with a slash and must not end with a slash. When a request is processed, the HTTP Service
will try to exact match the requested URI with a registered servlet. If not existent, it will remove the last '/' in the
URI and everything that follows, and try to match the remaining part, and so on.

An additional configuration Map can be optionally specified; if present, all the parameters contained will be copied in
the ServletContext object.

Finally, an HttpContext object can be optionally specified to handle authentication, mime type and resource mapping. The
`HttpContext` interface is quite simple:

```java
public interface HttpContext {
    /** Returns the mime type of the specified resource */
    String getMimeType(java.lang.String name);

    /** Returns the URL to retrieve the specified resource */
    URL getResource(java.lang.String name);

    /** Manages security for the specified request */
    boolean handleSecurity(HttpServletRequest request, HttpServletResponse response);
}
```

The use of a custom `HttpContext` is typical when you want to serve static contents with the HTTP Service. Let's first
see an example of resource registration **without** `HttpContext`:

```java
public class Activator implements BundleActivator {
    private ServiceTracker httpTracker;

    public void start(BundleContext context) throws Exception {
        httpTracker = new ServiceTracker(context, HttpService.class.getName(), null) {
          public void removedService(ServiceReference reference, Object service) {
            // HTTP service is no longer available, unregister our resources...
            try {
               ((HttpService) service).unregister("/static");
            } catch (IllegalArgumentException exception) {
               // Ignore; servlet registration probably failed earlier on...
            }
          }

          public Object addingService(ServiceReference reference) {
            // HTTP service is available, register our resources...
            HttpService httpService = (HttpService) this.context.getService(reference);
            try {
              httpService.registerResources("/static", "/etc/www", null);
            } catch (Exception exception) {
              exception.printStackTrace();
            }
            return httpService;
          }
      };
      // start tracking all HTTP services...
      httpTracker.open();
    }

    public void stop(BundleContext context) throws Exception {
      // stop tracking all HTTP services...
      httpTracker.close();
    }
}
```

As a result of the `httpService.registerResources("/static", "/etc/www", null)` code, all the files available under
`/etc/www` will be exposed under `/static` (e.g. `http://localhost:8080/static/001.jpg` will render the
`/etc/www/001.jpg`). However, the example above can be simplistic in practice; the `HttpContext` object is the solution
to customize the resource handling.

For instance, you can set the define more complex URI to file mappings overriding the `HttpContext.getResource` method,
or the correct MIME type implementing the method `HttpContext.getMimeType` like in the example:

```java
//....
public String getMimeType(String file) {
    if (file.endsWith(".jpg") {
        return "image/jpeg";  
    } else if (file.endsWith(".png")) {
        return "image/png";  
    } else {  
        return "text/html";  
    }
}
//....
```

If you implement a customised `HttpContext` object, don't forget to specify it as third parameter of the `registerResources` method invocation:

```java
// ....
public Object addingService(ServiceReference reference) {
    // HTTP service is available, register our resources...
    HttpService httpService = (HttpService) this.context.getService(reference);
    try {
        // explicitly use our own context as 3rd parameter...
        httpService.registerResources("/static", "/etc/www", new MyHttpContext());
    } catch (Exception exception) {
        exception.printStackTrace();
    }
    return httpService;
}
// ....
```

## Using the Servlet Bridge

The servlet bridge is used if you want to use the HTTP service inside a WAR deployed on a 3rd part applicaiton server. A
little setup is needed for this to work:

  1. deploy `org.apache.felix.http.proxy` jar file inside the web application (`WEB-INF/lib`);
  2. in a startup listener (like `ServletContextListener`) set the BundleContext as a servlet context attribute (see [example](https://github.com/apache/felix-dev/blob/master/http/samples/bridge/src/main/java/org/apache/felix/http/samples/bridge/StartupListener.java);
  3. define `org.apache.felix.http.proxy.ProxyServlet` inside your `web.xml` and register it to serve on all requests `/*` (see [example](https://github.com/apache/felix-dev/blob/master/http/samples/bridge/src/main/webapp/WEB-INF/web.xml);
  4. define `org.apache.felix.http.proxy.ProxyListener` as a `<listener>` in your `web.xml` to allow HTTP session related events to be forwarded (see the section of Servlet API Event forwarding below and [example](https://github.com/apache/felix-dev/blob/master/http/samples/bridge/src/main/webapp/WEB-INF/web.xml);
  5. be sure to add `javax.servlet;javax.servlet.http;version=2.6` to OSGi system packages (`org.osgi.framework.system.packages`);
  6. deploy `org.apache.felix.http.bridge` (or `org.apache.felix.http.bundle`) inside the OSGi framework.

A detailed example can be found [here](https://github.com/apache/felix-dev/blob/master/http/samples/bridge).


## Using the SSL filter

This filter provides you means to transparently handle [SSL termination proxies](https://en.wikipedia.org/wiki/SSL_termination_proxy),
allowing your servlets and filters to work *like they were accessed directly through HTTPS*. This filter is useful when
deploying applications in large datacenters where frontend load-balancers distribute the load among several servers in
the same datacenter by stripping the SSL encryption.

The SSL filter can be [configured](#ssl-filter-configuration-properties) to let it detect whether the request was originating from
an SSL connection. There are several non-standard request headers in use:

| Header name | Used value | Description |
|--|--|--|
| `X-Forwarded-Proto` | `https` | used by [Amazon ELBs](https://docs.aws.amazon.com/ElasticLoadBalancing/latest/DeveloperGuide/TerminologyandKeyConcepts.html#x-forwarded-proto) and [Nginx](https://wiki.nginx.org/SSL-Offloader) and more or less the de-facto standard to indicate a SSL-terminated connection. |
| `X-Forwarded-Protocol` | `https` | alternative to `X-Forwarded-Proto`. |
| `X-Forwarded-SSL` | `on` | non-standard way used by [some applications](https://wiki.apache.org/couchdb/Nginx_As_a_Reverse_Proxy). |
| `Front-End-Https` | `on` | used by [Microsoft](https://technet.microsoft.com/en-us/library/aa997519%28v=exchg.65%29.aspx) to indicate a SSL-terminated connection. |

In case a client connected using a certificate, this certificate can be forwarded as well by the SSL filter. Several
non-standard request headers are used for this:

| Header | Description |
|--|--|
| `X-Forwarded-SSL-Certificate` | The de-facto(?) standard used to forward the client certificate by a proxy. |
| `X-Forwarded-SSL-Client-Cert` | Alternative header used by some proxies. |

**Note**: instead of deploying the SSL filter bundle, you can also set the `org.apache.felix.proxy.load.balancer.connection.enable` property to `true` in order to achieve the same effect.

## Configuration Properties

The service can both be configured using OSGi environment properties and using Configuration Admin. The service PID for
this service is `"org.apache.felix.http"`. If you use both methods, Configuration Admin takes precedence. The following
properties can be used (some legacy property names still exist but are not documented here on purpose). As properties might change over time, the actual list of properties can be found [here for the Jetty 12 bundle](https://github.com/apache/felix-dev/blob/master/http/jetty12/src/main/java/org/apache/felix/http/jetty/internal/JettyConfig.java) and [here for the Jetty 11 bundle](https://github.com/apache/felix-dev/blob/master/http/jetty/src/main/java/org/apache/felix/http/jetty/internal/JettyConfig.java).

| Property                                                 | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
|----------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `org.apache.felix.http.host`                             | Host name or IP Address of the interface to listen on. The default is `null` causing Jetty to listen on all interfaces.                                                                                                                                                                                                                                                                                                                                              |
| `org.osgi.service.http.port`                             | The port used for servlets and resources available via HTTP. The default is `8080`. See [port settings below](#http-port-settings) for additional information. A negative port number has the same effect as setting `org.apache.felix.http.enable` to `false`.                                                                                                                                                                                                      |
| `org.osgi.service.http.port.secure`                      | The port used for servlets and resources available via HTTPS. The default is `8443`. See [port settings below](#http-port-settings) for additional information. A negative port number has the same effect as setting `org.apache.felix.https.enable` to `false`.                                                                                                                                                                                                    |
| `org.apache.felix.http.context_path`                     | The servlet Context Path to use for the Http Service. If this property is not configured it  defaults to "/". This must be a valid path starting with a slash and not  ending with a slash (unless it is the root context).                                                                                                                                                                                                                                          |
| `org.apache.felix.http.timeout`                          | Connection timeout in milliseconds. The default is `60000` (60 seconds).                                                                                                                                                                                                                                                                                                                                                                                             |
| `org.apache.felix.http.session.timeout`                  | Allows for the specification of the Session life time as a number of minutes. This property serves the same purpose as the `session-timeout` element in a Web Application descriptor. The default is "0" (zero) for no timeout at all.                                                                                                                                                                                                                               |
| `org.apache.felix.http.enable`                           | Flag to enable the use of HTTP. The default is `true`.                                                                                                                                                                                                                                                                                                                                                                                                               |
| `org.apache.felix.https.enable`                          | Flag to enable the user of HTTPS. The default is `false`.                                                                                                                                                                                                                                                                                                                                                                                                            |
| `org.apache.felix.https.keystore`                        | The name of the file containing the keystore.                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `org.apache.felix.https.keystore.password`               | The password for the keystore.                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `org.apache.felix.https.keystore.key.password`           | The password for the key in the keystore.                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `org.apache.felix.https.truststore`                      | The name of the file containing the truststore.                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `org.apache.felix.https.truststore.type`                 | The type of truststore to use. The default is `JKS`.                                                                                                                                                                                                                                                                                                                                                                                                                 |
| `org.apache.felix.https.truststore.password`             | The password for the truststore.                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `org.apache.felix.https.jetty.ciphersuites.excluded`     | Configures comma-separated list of SSL cipher suites to *exclude*. Default is `null`, meaning that no cipher suite is excluded.                                                                                                                                                                                                                                                                                                                                      |
| `org.apache.felix.https.jetty.ciphersuites.included`     | Configures comma-separated list of SSL cipher suites to *include*. Default is `null`, meaning that the default cipher suites are used.                                                                                                                                                                                                                                                                                                                               |
| `org.apache.felix.https.jetty.protocols.excluded`        | Configures comma-separated list of SSL protocols (e.g. SSLv3, TLSv1.0, TLSv1.1, TLSv1.2) to *exclude*. Default is `null`, meaning that no protocol is excluded.                                                                                                                                                                                                                                                                                                      |
| `org.apache.felix.https.jetty.protocols.included`        | Configures comma-separated list of SSL protocols to *include*. Default is `null`, meaning that the default protocols are used.                                                                                                                                                                                                                                                                                                                                       |
| `org.apache.felix.https.clientcertificate`               | Flag to determine if the HTTPS protocol requires, wants or does not use client certificates. Legal values are `needs`, `wants` and `none`. The default is `none`.                                                                                                                                                                                                                                                                                                    |
| `org.apache.felix.http.jetty.headerBufferSize`           | Size of the buffer for request and response headers, in bytes. Default is 16 KB.                                                                                                                                                                                                                                                                                                                                                                                     |
| `org.apache.felix.http.jetty.requestBufferSize`          | Size of the buffer for requests not fitting the header buffer, in bytes. Default is 8 KB.                                                                                                                                                                                                                                                                                                                                                                            |
| `org.apache.felix.http.jetty.responseBufferSize`         | Size of the buffer for responses, in bytes. Default is 24 KB.                                                                                                                                                                                                                                                                                                                                                                                                        |
| `org.apache.felix.http.jetty.maxFormSize`                | The maximum size accepted for a form post, in bytes. Defaults to 200 KB.                                                                                                                                                                                                                                                                                                                                                                                             |
| `org.apache.felix.http.mbeans`                           | If `true`, enables the MBean server functionality. The default is `false`.                                                                                                                                                                                                                                                                                                                                                                                           |
| `org.apache.felix.http.jetty.sendServerHeader`           | If `false`, the `Server` HTTP header is no longer included in responses. The default is `false`.                                                                                                                                                                                                                                                                                                                                                                     |
| `org.eclipse.jetty.servlet.SessionCookie`                | Name of the cookie used to transport the Session ID. The default is `JSESSIONID`.                                                                                                                                                                                                                                                                                                                                                                                    |
| `org.eclipse.jetty.servlet.SessionURL`                   | Name of the request parameter to transport the Session ID. The default is `jsessionid`.                                                                                                                                                                                                                                                                                                                                                                              |
| `org.eclipse.jetty.servlet.SessionDomain`                | Domain to set on the session cookie. The default is `null`.                                                                                                                                                                                                                                                                                                                                                                                                          |
| `org.eclipse.jetty.servlet.SessionPath`                  | The path to set on the session cookie. The default is the configured session context path ("/").                                                                                                                                                                                                                                                                                                                                                                     |
| `org.eclipse.jetty.servlet.MaxAge`                       | The maximum age value to set on the cookie. The default is "-1".                                                                                                                                                                                                                                                                                                                                                                                                     |
| `org.eclipse.jetty.UriComplianceMode`                    | The URI compliance mode to set. The default is [DEFAULT](https://eclipse.dev/jetty/javadoc/jetty-12/org/eclipse/jetty/http/UriCompliance.html#DEFAULT). See [documentation](https://eclipse.dev/jetty/documentation/jetty-12/programming-guide/index.html#pg-server-compliance-uri.) and [possible modes](https://github.com/jetty/jetty.project/blob/jetty-12.0.x/jetty-core/jetty-http/src/main/java/org/eclipse/jetty/http/UriCompliance.java#L186C107-L186C113). Use with caution, as it may have [security implications](https://github.com/apache/felix-dev/pull/308#issuecomment-2438913766). |
| `org.apache.felix.proxy.load.balancer.connection.enable` | Set this to `true` when running Felix HTTP behind a (offloading) proxy or load balancer which rewrites the requests. The default is `false`.                                                                                                                                                                                                                                                                                                                         |
| `org.apache.felix.http.runtime.init.`                    | Properties starting with this prefix are added as service registration properties to the HttpServiceRuntime service. The prefix is removed for the property name.                                                                                                                                                                                                                                                                                                    |
| `org.apache.felix.jetty.gziphandler.enable`              | Whether the server should use a server-wide gzip handler. Default is false.                                                                                                                                                                                                                                                                                                                                                                                          |
| `org.apache.felix.jetty.gzip.minGzipSize`                | The minimum response size to trigger dynamic compression. Default is GzipHandler.DEFAULT_MIN_GZIP_SIZE.                                                                                                                                                                                                                                                                                                                                                              |
| `org.apache.felix.jetty.gzip.inflateBufferSize`          | The size in bytes of the buffer to inflate compressed request, or <= 0 for no inflation. Default is -1.                                                                                                                                                                                                                                                                                                                                                              |
| `org.apache.felix.jetty.gzip.syncFlush`                  | True if Deflater#SYNC_FLUSH should be used, else Deflater#NO_FLUSH will be used. Default is false.                                                                                                                                                                                                                                                                                                                                                                   |
| `org.apache.felix.jetty.gzip.includedMethods`            | The additional http methods to include in compression. Default is none.                                                                                                                                                                                                                                                                                                                                                                                              |
| `org.apache.felix.jetty.gzip.excludedMethods`            | The additional http methods to exclude in compression. Default is none.                                                                                                                                                                                                                                                                                                                                                                                              |
| `org.apache.felix.jetty.gzip.includedPaths`              | The additional path specs to include. Inclusion takes precedence over exclusion. Default is none.                                                                                                                                                                                                                                                                                                                                                                    |
| `org.apache.felix.jetty.gzip.excludedPaths`              | The additional path specs to exclude. Inclusion takes precedence over exclusion. Default is none.                                                                                                                                                                                                                                                                                                                                                                    |
| `org.apache.felix.jetty.gzip.includedMimeTypes`          | The included mime types. Inclusion takes precedence over exclusion. Default is none.                                                                                                                                                                                                                                                                                                                                                                                 |
| `org.apache.felix.jetty.gzip.excludedMimeTypes`          | The excluded mime types. Inclusion takes precedence over exclusion. Default is none.                                                                                                                                                                                                                                                                                                                                                                                 |
| `org.apache.felix.http2.enable`                          | Whether to enable HTTP/2. Default is false.                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `org.apache.felix.jetty.http2.maxConcurrentStreams`      | The max number of concurrent streams per connection. Default is 128.                                                                                                                                                                                                                                                                                                                                                                                                 |
| `org.apache.felix.jetty.http2.initialStreamRecvWindow`   | The initial stream receive window (client to server). Default is 524288.                                                                                                                                                                                                                                                                                                                                                                                             |
| `org.apache.felix.jetty.http2.initialSessionRecvWindow`  | The initial session receive window (client to server). Default is 1048576.                                                                                                                                                                                                                                                                                                                                                                                           |
| `org.apache.felix.jetty.alpn.protocols`                  | The ALPN protocols to consider. Default is h2, http/1.1.                                                                                                                                                                                                                                                                                                                                                                                                             |
| `org.apache.felix.jetty.alpn.defaultProtocol`            | The default protocol when negotiation fails. Default is http/1.1.                                                                                                                                                                                                                                                                                                                                                                                                    |
| `org.apache.felix.jakarta.websocket.enable`              | Enables Jakarta websocket support. Default is false.                                                                                                                                                                                                                                                                                                                                                                                                                 |
| `org.apache.felix.jetty.websocket.enable`                | Enables Jetty websocket support. Default is false.                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `org.apache.felix.http.jetty.threadpool.max`             | The maximum number of threads in the Jetty thread pool. Default is unlimited. Works for both platform threads and virtual threads (Jetty 12 only).                                                                                                                                                                                                                                                                                                                   |
| `org.apache.felix.http.jetty.virtualthreads.enable`      | Enables using virtual threads in Jetty 12 (JDK 21 required). Default is false. When enabled, `org.apache.felix.http.jetty.threadpool.max` is used for a bounded virtual thread pool.                                                                                                                                                                                                                                                                                 |

### Multiple Servers

It is possible to configure several Http Services, each running on a different port. The first service can be configured as outlined above using the service PID for `"org.apache.felix.http"`. Additional servers can be configured through OSGi factory configurations using `"org.apache.felix.http"` as the factory PID. The properties for the configuration are outlined as above.

The default server using the PID `"org.apache.felix.http"` can be disabled by specifying a negative port and then all servers can be used through factory configurations.

### SSL filter configuration properties

The SSL-filter bundle supports the following configuration options, using the PID
`org.apache.felix.http.sslfilter.SslFilter` (no fallback to environment properties!):

| Property | Description |
|--|--|
| `ssl-forward.header` | Defines what HTTP header to look for in a request to determine whether a request is a forwarded SSL request. The default is `X-Forwarded-SSL`. |
| `ssl-forward.value` | Defines what HTTP header value to look for in a request to determine whether a request is a forwarded SSL request in a request. The default is `on`. |
| `ssl-forward-cert.header` | Defines what HTTP header to look for in a request to obtain the forwarded client certificate. The default is `X-Forwarded-SSL-Certificate`. |


### HTTP port settings

As of HTTP Jetty version 2.2.2, it is possible to assign a free port for HTTP or HTTPS automatically, based on certain
rules, for example, a range between 8000 and 9000. The syntax is based on the version ranges, as described in the OSGi
specification. The following forms are supported:

  * `*` or `0`: binds to the first available port;
  * `8000`: binds to port `8000`, failing if this port is already taken;
  * `[8000,9000]`: binds to a free port in the range 8000 (inclusive) and 9000 (inclusive);
  * `[8000,9000)`: binds to a free port in the range 8000 (inclusive) and 9000 (exclusive);
  * `(8000,9000]`: binds to a free port in the range 8000 (exclusive) and 9000 (inclusive);
  * `(8000,9000)`: binds to a free port in the range 8000 (exclusive) and 9000 (exclusive);
  * `[,9000)`: binds to a free port in the range 1 (inclusive) and 9000 (exclusive);
  * `[8000,)`: binds to a free port in the range 8000 (inclusive) and 65535 (exclusive).

Note that picking a port is *not* performed atomically and multiple instances can try to bind to the same port at the
same time.

## Servlet API Events

The Servlet API defines a number of `EventListener` interfaces to catch servlet or filter related events. As of HTTP
Service 2.1.0 most events generated by the servlet container are forwarded to interested service. To be registered to
receive events services must be registered with the respective `EventListener` interface:

| Interface | Description |
|--|--|
| `javax.servlet.ServletContextAttributeListener` | Events on servlet context attribute addition, change and removal. |
| `javax.servlet.ServletRequestAttributeListener` | Events on request attribute addition, change and removal. |
| `javax.servlet.ServletRequestListener` | Events on request start and end. |
| `javax.servlet.http.HttpSessionAttributeListener` | Events on session attribute addition, change and removal. To receive such events in a bridged environment, the `ProxyLister` must be registered with the servlet container. See the *Using the Servlet Bridge* section above. |
| `javax.servlet.http.HttpSessionListener` | Events on session creation and destroyal. To receive such events in a bridged environment, the `ProxyLister` must be registered with the servlet container. See the *Using the Servlet Bridge* section above. |

Of the defined `EventListener` interfaces in the Servlet API, the `javax.servlet.ServletContextListener` events are
currently not support (but will be in the near future). For one thing they do not make much sense in an OSGi
environment. On the other hand they are hard to capture and propagate. For example in a bridged environment the
`contextInitialized` event may be sent before the framework and any of the contained bundles are actually ready to act.
Likewise the `contextDestroyed` event may come to late.


## Servlet Context Notes

`ServletContext` instances are managed internally by the Http Service implementation. For each `HttpContext` instance
used to register one or more servlets and/or resources a corresponding `ServletContext` instance is created. These
`ServletContext` instances is partly based on the single `ServletContext` instance received from the Servlet Container
--- either embedded Jetty or some external Servlet Container when using the Http Service Bridge --- and partly based on
the provided `HttpContext` instance:

| Method(s) | Based on ... |
|-|-|
| `getContextPath`, `getContext`, `getMajorVersion`, `getMinorVersion`, `getServerInfo` | Servlet Containers `ServletContext` |
| `getResourcePaths` | `Bundle.getEntryPaths` of the bundle using the Http Service |
| `getResource`, `getResourceAsStream` | `HttpContext.getResource` |
| `getMimeType` | `HttpContext.getMimeType` |
| `getRequestDispatcher`, `getNamedDispatcher`, `getInitParameter`, `getServlet`, `getRealPath` | Always return `null` |
| `getInitParameterNames`, `getServlets`, `getServletNames` | Always returns empty `Enumeration` |
| `getAttribute`, `getAttributeNames`, `setAttribute`, `removeAttribute` | By default maintained for each `ServletContext` managed by the Http Service. If the `org.apache.felix.http.shared*servlet*context_attributes` framework property is set to `true` these methods are actually based on the `ServletContext` provided by the servlet container and thus attributes are shared amongst all `ServlectContext` instances, incl. the `ServletContext` provided by the servlet container |


## Examples

A set of simple examples illustrating the various features are available.

  * Whiteboard sample: <https://github.com/apache/felix-dev/tree/master/http/samples/whiteboard>
  * Servlet bridge sample: <https://github.com/apache/felix-dev/tree/master/http/samples/bridge/>

## Maven Artifacts

This is a list of the most recent artifacts at the time of writing this document. There might already be newer versions available:

```xml
<dependency>
    <groupId>org.apache.felix</groupId>
    <artifactId>org.apache.felix.http.servlet-api</artifactId>
    <version>3.0.0</version>
</dependency>
<dependency>
    <groupId>org.apache.felix</groupId>
    <artifactId>org.apache.felix.http.jetty12</artifactId>
    <version>1.0.2</version>
</dependency>
<dependency>
    <groupId>org.apache.felix</groupId>
    <artifactId>org.apache.felix.http.bridge</artifactId>
    <version>5.1.6</version>
</dependency>
<dependency>
    <groupId>org.apache.felix</groupId>
    <artifactId>org.apache.felix.http.proxy</artifactId>
    <version>4.0.0</version>
</dependency>
<dependency>
    <groupId>org.apache.felix</groupId>
    <artifactId>org.apache.felix.http.wrappers</artifactId>
    <version>1.1.2</version>
</dependency>
<dependency>
    <groupId>org.apache.felix</groupId>
    <artifactId>org.apache.felix.http.sslfilter</artifactId>
    <version>2.0.2</version>
</dependency>
```
