# Apache Felix Configuration Admin Service

The OSGi Componendium Configuration Admin Service specifies a service, which allows for easy management of configuration data for configurable components. Basically a configuration is a list of name-value pairs. A configuration is managed by management applications by asking the Configuration Admin Service for such configuration. After updating the configuration, it is sent back to the Configuration Admin Service. The Configuration Admin Service is like a central hub, which cares for persisting this configuration and also for distributing the configuration to interested parties. One class of such parties are the components to be configured. These are registered as `ManagedService` services. There is also a notion of `ManagedServiceFactory`, which allows for multiple configurations of the same kind to be applied.

For more information, its suggested you read [Chapter 104, Configuration Admin Service Specification](https://osgi.org/specification/osgi.cmpn/7.0.0/service.cm.html), of the OSGi Compendium Services Specification book. 

For a starter this page sets out to describe how you can create a component, which is interested in some configuration. As such this page is at its very beginning just highlighting the simplest of all cases: A single component being interested in its configuration.

If you use [OSGi Declarative Services](https://osgi.org/specification/osgi.cmpn/7.0.0/service.component.html), that implementation takes care of all the low level configuration handling and you don't need to interact with Configuration Admin directly.

## Project Info

The Apache Felix Configuration Admin Service provided the reference implementation for the OSGi specification. The latest release of this implementation implements specification version 1.6 of the [OSGi R7 Compendium release](https://osgi.org/specification/osgi.cmpn/7.0.0/service.cm.html).

## `ManagedService` Example

Consider you have requirement for some configuration, say the line length of a pretty printer. You want to have this configurable through configuration admin.

You need the following parts:

 * A service PID identifying the configuration
 * A `ManagedService` to receive the configuration
 * Name(s) for the configuration property/ies

The PID is just a string, which must be globally unique. Assuming a simple case where your PrettyPrinter configurator receives the configuration has a unique class name, you may well use that name. So lets assume, our `ManagedService` is called `org.sample.PrettyPrinterConfigurator` and that name is also used as the PID. For more information on the Service PID, refer to Section 104.3, The Persistent Identity of the OSGi Compendium Services Specification.

The class would be:

       package org.sample;
       class PrettyPrinterConfigurator implements ManagedService {
           public void update(Dictionary props)
               throws ConfigurationException {
               if (props == null) {
                   // no configuration from configuration admin
                   // or old configuration has been deleted
               } else {
                   // apply configuration from config admin
               }
           }
       }


Now, in your bundle activator's start() method you register the `PrettyPrinterConfigurator` as a `ManagedService`:

    ...
    private ServiceRegistration ppcService;
    public void start(BundleContext context) {
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("service.pid", "org.sample.PrettyPrinterConfigurator");
        ppcService = context.registerService(ManagedService.class.getName(),
            new PrettyPrinterConfigurator(), props);
    }
    public void stop(BundleContext context) {
        if (ppcService != null) {
            ppcService.unregister();
            ppcService = null;
        }
    }
    ...


That's more or less it. You may now go on to use your favourite tool to create and edit the configuration for the `PrettyPrinterConfigurator`, for example something like this:


    Configuration config = configurationAdmin.getConfiguration(
        "org.sample.PrettyPrinterConfigurator");
    Dictionary props = config.getProperties();
    
    // if null, the configuration is new
    if (props == null) {
        props = new Hashtable();
    }
    
    // set some properties
    props.put(..., ...);
    
    // update the configuration
    config.update(props);


After the call to `update` the Configuration Admin service persists the new configuration data and sends an update to the `ManagedService` registered with the service PID `org.sample.PrettyPrinterConfigurator`, which happens to be our `PrettyPrinterConfigurator` class as expected.


## ManagedServiceFactory example

Registering a service as ManagedServiceFactory means that it will be able to receive several different configuration dictionaries; that's particularly useful when we want to create a Service Factory, that is, a service responsible for creating multiple instances of a specific service.

A ManagedServiceFactory service needs to implement the ManagedServiceFactory interface, as showed in the example.


    public class SmsSenderFactory implements ManagedServiceFactory
    {   
        Map existingServices = new HashMap();
        
        public void updated(String pid, Dictionary dictionary) throws ConfigurationException 
        {
            // invoked when a new configuration dictionary is assigned
            // to service 'pid'. 
            if (existingServices.containsKey(pid))  //the service already exists
            {
                MyService service = (MyService) existingServices.get(pid);
                service.configure(dictionary);
            }
            else //configuration dictionary for a new service
            {
                MyService service = createNewServiceInstance();
                service.configure(dictionary);
                existingServices.put(pid, service);
            }
        }
        
        public void deleted(String pid) 
        {
            // invoked when the service 'pid' is deleted
            existingServices.remove(pid);
        }
    
        public String getName() 
        {
            return "test.smssenderfactory";
        }
    }


The example above shows that, differently from the ManagedService, the ManagedServiceFactory is designed to manage multiple instances of a service. In fact, the `update` method accept a `pid` and a `Dictionary` as arguments, thus allowing to associate a certain configuration dictionary to a particular service instance (identified by the `pid`).

Note also that the ManagedServiceFactory interface requires to implement (besides the `getName` method) a `delete` method: this method is invoked when the Configuration Admin Service asks the ManagedServiceFactory to delete a specific service instance.

The registration of a `ManagedServiceFactory` follows the same steps of the `ManagedService` example:


    private ServiceRegistration factoryService;
    public void start(BundleContext context) {
        Dictionary props = new Hashtable();
        props.put("service.pid", "test.smssenderfactory");
        factoryService = context.registerService(ManagedServiceFactory.class.getName(),
            new SmsSenderFactory(), props);
    }
    public void stop(BundleContext context) {
        if (factoryService != null) {
            factoryService.unregister();
            factoryService = null;
        }
    }
    ...


Finally, using the ConfigurationAdmin interface, it is possible to send new or updated configuration dictionaries to the newly created ManagedServiceFactory:


    public class Activator implements BundleActivator 
    {   
        private List configurationList = new ArrayList();  
         
        public void start(BundleContext bundleContext) throws Exception 
        {  
            ServiceReference configurationAdminReference = 
                bundleContext.getServiceReference(ConfigurationAdmin.class.getName());  
                  
            if (configurationAdminReference != null) 
            {  
                ConfigurationAdmin confAdmin = (ConfigurationAdmin) bundleContext.getService(configurationAdminReference);  
                  
                Configuration configuration = confAdmin.createFactoryConfiguration("test.smssenderfactory", null);  
                Dictionary properties = createServiceProperties();
                configuration.update(properties);  
                  
                //Store in the configurationList the configuration object, the dictionary object
                //or configuration.getPid()  for future use  
                configurationList.add(configuration);  
            }  
        }   
    }  



## Apache Felix Implementation Details

The Apache Felix implementation of the Configuration Admin Service specification has a few specialities, which may be of interest when deploying it. These are described here.


### Configuration Properties

The Apache Felix implementation is configurable with Framework properties. Here is a short table listing the properties. Please refer to the later sections for a description of these properties.

| Property | Type | Default Value | Description |
|--|--|--|--|
| `felix.cm.loglevel` | int | `2` | Logging level to use in the absence of an OSGi LogService. See the *Logging* section below. |
| `felix.cm.dir` | String | `BundleContext.getDataFile("config")` | Location of the Configuration Admin configuration files. See the *Configuration Files* section below. |

### Logging

Logging goes to the OSGi LogService if such a service is registered int the OSGi framework. If no OSGi LogService is registered, the log output is directed to the Java platform standard error output (`System.err`).

To limit the output in the absence of an OSGi LogService, the `felix.cm.loglevel` framework property may be set to an integer value limiting the level of the log messages actually written: Only messages whose level is lower than or equal to the limit is actually written. All other messages are discarded.

The log levels correspond to the predefined log levels of the OSGi Log Service Specification as listed in the following table:

| Level Number | LogService Constant | Remark |
|--|--|--|
| 1 | LOG_ERROR | Used for error messages |
| 2 | LOG_WARNING | Used for warning messages. This is the default value of the `felix.cm.loglevel` property if it is not set or if the value cannot be converted to an integer. |
| 3 | LOG_INFO | Used for informational messages |
| 4 | LOG_DEBUG | Used for debug messages |

*Note*: The `felix.cm.loglevel` property is ignored if an OSGi LogService is actually used for logging because it is then the responsibility of the LogService to limit the actual log output.


### Configuration Files

By default the Apache Felix Configuration Admin Implementation stores the configuration data in the platform filesystem. The location of the configuration data can be configured with the `felix.cm.dir` framework property.

The resolution of the location using the `felix.cm.dir` and the `BundleContext` is implemented according to the following algorithm.

1. If the `felix.cm.dir` property is not set, a directory named `config` is used inside the persistent storage area of the Apache Felix Configuration Admin Service bundle is used. This is the default behaviour.
1. If the `felix.cm.dir` property is not set and the framework does not support persistent storage area for bundles in the filesystem, the `config` directory is used in the current working directory as specified in the `user.dir` system property is assumed.
1. Otherwise the `felix.cm.dir` property value is used as the directory name to take the configuration data.

The result of these steps may be a relative file. In this case and if the framework provides access to persistent storage area, the directory name is resolved as being inside the persistent storage area. Otherwise the directory name is resolved to an absolute path calling the File.getAbsoluteFile() method.

If a non-directory file exists as the location found in the previous step or the named directory (including any parent directories) cannot be created, the configuration data cannot be stored in the filesystem. Generally this will result in failure to store configuration data at all, except if there is a `org.apache.felix.cm.PersistenceManager` service registered, which is then used.
