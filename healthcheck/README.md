# Felix Health Checks

Based on a simple `HealthCheck` SPI interface, Felix Health Checks are used to check the health/availability of Apache Felix instances at runtime based on inputs like

* OSGi framework status
* JMX MBean attribute values
* OSGi service(s) / SCR component(s) availablility
* ... or any context information retrievable via any API

Health checks are easily extensible either by

* configuring the default HealthCheck services in bundle general checks (can be configuration or scripts, see [out-of-the-box checks](#general-purpose-health-checks-available-out-of-the-box)). **For simple setups, the out-of-the-box health checks are often sufficient**
* or by implementing your own HealthCheck to cater special requirements (this is done by just registering a service for this interface)

There are various ways to [execute health checks](#executing-health-checks) - this is a good starting point to get familiar with how health checks work.

See also:

* [Source code for the HealthCheck modules](http://svn.apache.org/repos/asf/felix/trunk/healthcheck)
* adaptTo() slides about Health Checks (from the time when they were part of Apache Sling):
    * [adaptTo() 2019 - Felix Health Checks](https://adapt.to/2019/en/schedule/felix-health-checks.html)
    * [adaptTo() 2014 - New features of the Sling Health Check](https://adapt.to/2014/en/schedule/new-features-of-the-sling-health-check.html)
    * [adaptTo() 2013 - Automated self-testing and health check of live Sling instances](https://adapt.to/2013/en/schedule/18_healthcheck.html)

## Use cases
Generally health checks have two high level use cases:

* **Load balancers/orchestration engines can query the health of an instance** and decide when to route requests to it
* **Operations teams checking instances** for their internal state **manually**

The strength of Health Checks are to surface internal state for external use:

* Check that all OSGi bundles are up and running
* Verify that performance counters are in range
* Ping external systems and raise alarms if they are down
* Run smoke tests at system startup
* Check that demo content has been removed from a production system
* Check that demo accounts are disabled

The health check subsystem uses tags to select which health checks to execute so you can for example execute just the _performance_ or _security_ health
checks once they are configured with the corresponding tags.

The out of the box health check services also allow for using them as JMX aggregators and processors, which take JMX
attribute values as input and make the results accessible via JMX MBeans.

## Implementing `HealthCheck`s

Health checks can be contributed by any bundle via the provided SPI interface. It is best practice to implement a health check as part of the bundle that contains the functionality being checked.

## The `HealthCheck` SPI interface

A `HealthCheck` is just an OSGi service that returns a `Result`.

```
    public interface HealthCheck {

        /** Execute this health check and return a {@link Result}
         *  This is meant to execute quickly, access to external
         *  systems, for example, should be managed asynchronously.
         */
        public Result execute();
    }
```

A simple health check implementation might look like follows:

```
    public class SampleHealthCheck implements HealthCheck {

        @Override
        public Result execute() {
            FormattingResultLog log = new FormattingResultLog();
            ...
            log.info("Checking my context {}", myContextObject);
            if(myContextObject.retrieveStatus() != ...expected value...) {
                log.warn("Problem with ...");
            }
            if(myContextObject.retrieveOtherStatus() != ...expected value...) {
                log.critical("Cricital Problem with ...");
            }
            return new Result(log);
        }

    }
```

The `Result` is a simple immutable class that provides a `Status` via `getStatus()` (OK, WARN, CRITICAL etc.) and one or more log-like messages that
can provide more info about what, if anything, went wrong.

Instead of using Log4j side by side with ResultLog/FormattingResultLog it is recommended to turn on `autoLogging` in the [health check executor config](#configuring-the-health-check-executor) in order to keep the implementation classes DRY. **NOTE:** This feature does not work with checks implemented against the legacy Sling interface.

### Semantic meaning of health check results
In order to make health check results aggregatable in a reasonable way, it is important that result status values are used in a consistent way across different checks. When implementing custom health checks, comply to the following table:

Status | System is functional | Meaning | Actions possible for machine clients | Actions possible for human clients
--- | --- | --- | --- | ---
OK | yes | Everything is ok. | <ul><li>If system is not actively used yet, a load balancer might decide to take the system to production after receiving this status for the first time.</li><li>Otherwise no action needed</li></ul> | Response logs might still provide information to a human on why the system currently is healthy. E.g. it might show 30% disk used which indicates that no action will be required for a long time
WARN | yes | **Tendency to CRITICAL** <br>System is fully functional but actions are needed to avoid a CRITICAL status in the future | <ul><li>Certain actions can be configured for known, actionable warnings, e.g. if disk space is low, it could be dynamically extended using infrastructure APIs if on virtual infrastructure)</li><li>Pass on information to monitoring system to be available to humans (in other aggregator UIs)</li></ul> | Any manual steps that a human can perform based on their knowledge to avoid the system to get to CRITICAL state
TEMPORARILY_UNAVAILABLE *) | no | **Tendency to OK** <br>System is not functional at the moment but is expected to become OK (or at least WARN) without action. An health check using this status is expected to turn CRITICAL after a certain period returning TEMPORARILY_UNAVAILABLE | <ul><li>Take out system from load balancing</li><li>Wait until TEMPORARILY_UNAVAILABLE status turns into either OK or CRITICAL</li></ul> | Wait and monitor result logs of health check returning TEMPORARILY_UNAVAILABLE
CRITICAL | no | System is not functional and must not be used | <ul><li>Take out system from load balancing</li><li>Decommission system entirely and re-provision from scratch</li></ul>  | Any manual steps that a human can perform based on their knowledge to bring the system back to state OK
HEALTH\_CHECK\_ERROR | no | **Actual status unknown** <br>There was an error in correctly calculating one of the status values above. Like CRITICAL but with the hint that the health check probe itself might be the problem (and the system could well be in state OK) | <ul><li>Treat exactly the same as CRITICAL</li></ul>  | Fix health check implementation or configuration to ensure a correct status can be calculated

*) The health check executor automatically turns checks that coninuosly return `TEMPORARILY_UNAVAILABLE` into `CRITICAL` after a certain grace period, see [Configuring the Health Check Executor](#configuring-the-health-check-executor)

### Configuring Health Checks

`HealthCheck` services are created via OSGi configurations. Generic health check service properties are interpreted by the health check executor service. Custom health check service properties can be used by the health check implementation itself to configure its behaviour.

The following generic Health Check properties may be used for all checks (**all service properties are optional**):

Property    | Type     | Description
----------- | -------- | ------------
hc.name     | String   | The name of the health check as shown in UI
hc.tags     | String[] | List of tags: Both Felix Console Plugin and Health Check servlet support selecting relevant checks by providing a list of tags
hc.mbean.name | String | Makes the HC result available via given MBean name. If not provided no MBean is created for that `HealthCheck`
hc.async.cronExpression | String | Executes the health check asynchronously using the cron expression provided. Use this for **long running health checks** to avoid execution every time the tag/name is queried. Prefer configuring a HealthCheckMonitor if you only want to regularly execute a HC.
hc.async.intervalInSec | Long | Async execution like `hc.async.cronExpression` but using an interval
hc.resultCacheTtlInMs | Long | Overrides the global default TTL as configured in health check executor for health check responses
hc.keepNonOkResultsStickyForSec | Long | If given, non-ok results from past executions will be taken into account as well for the given seconds (use Long.MAX_VALUE for indefinitely). Useful for unhealthy system states that disappear but might leave the system at an inconsistent state (e.g. an event queue overflow where somebody needs to intervene manually) or for checks that should only go back to OK with a delay (can be useful for load balancers).

### Annotations to simplify configuration of custom Health Checks

To configure the defaults for the service properties [above](#configuring-health-checks), the following annotations can be used:

    // standard OSGi
    @Component
    @Designate(ocd = MyCustomCheckConfig.class, factory = true)

    // to set `hc.name` and `hc.tags`
    @HealthCheckService(name = "Custom Check Name", tags= {"tag1", "tag2"})

    // to set `hc.async.cronExpression` or  `hc.async.intervalInSec`
    @Async(cronExpression="0 0 12 1 * ?" /*, intervalInSec = 60 */)

    // to set `hc.resultCacheTtlInMs`:
    @ResultTTL(resultCacheTtlInMs = 10000)

    // to set `hc.mbean.name`:
    @HealthCheckMBean(name = "MyCustomCheck")

    // to set `hc.keepNonOkResultsStickyForSec`:
    @Sticky(keepNonOkResultsStickyForSec = 10)
    public class MyCustomHealthCheck implements HealthCheck {
    ...


## General purpose health checks available out-of-the-box

The following checks are contained in bundle `org.apache.felix.healthcheck.generalchecks` and can be activated by simple configuration:

Check | PID | Factory | Description
--- | --- | --- | ---
Framework Startlevel | org.apache.felix.hc.generalchecks.FrameworkStartCheck | no | Checks the OSGi framework startlevel - `targetStartLevel` allows to configure a target start level, `targetStartLevel.propName` can be used to read it from the framework/system properties.
Services Ready | org.apache.felix.hc.generalchecks.ServicesCheck | yes | Checks for the existance of the given services. `services.list` can contain simple service names or filter expressions
Components Ready | org.apache.felix.hc.generalchecks.DsComponentsCheck | yes | Checks for the existance of the given components. Use `components.list` to list required active components (use component names)
Bundles Started | org.apache.felix.hc.generalchecks.BundlesStartedCheck | yes | Checks for started bundles - `includesRegex` and `excludesRegex` control what bundles are checked.
Disk Space | org.apache.felix.hc.generalchecks.DiskSpaceCheck | yes | Checks for disk space usage at the given paths `diskPaths` and checks them against thresholds `diskUsedThresholdWarn` (default 90%) and diskUsedThresholdCritical (default 97%)
Memory | org.apache.felix.hc.generalchecks.MemoryCheck | no | Checks for Memory usage - `heapUsedPercentageThresholdWarn` (default 90%) and `heapUsedPercentageThresholdCritical` (default 99%) can be set to control what memory usage produces status `WARN` and `CRITICAL`
CPU | org.apache.felix.hc.generalchecks.CpuCheck | no | Checks for CPU usage - `cpuPercentageThresholdWarn` (default 95%) can be set to control what CPU usage produces status `WARN` (check never results in `CRITICAL`)
Thread Usage | org.apache.felix.hc.generalchecks.ThreadUsageCheck | no | Checks via `ThreadMXBean.findDeadlockedThreads()` for deadlocks and analyses the CPU usage of each thread via a configurable time period (`samplePeriodInMs` defaults to 200ms). Uses `cpuPercentageThresholdWarn` (default 95%) to `WARN` about high thread utilisation.
JMX Attribute Check | org.apache.felix.hc.generalchecks.JmxAttributeCheck | yes | Allows to check an arbitrary JMX attribute (using the configured mbean `mbean.name`'s attribute `attribute.name`) against a given constraint `attribute.value.constraint` (see [Constraints](#constraints)). Can check multiple attributes by providing additional config properties with numbers: `mbean2.name` (defaults to `mbean.name` if ommitted), `attribute2.name` and `attribute2.value.constraint` and `mbean3.name`, `attribute3.name` and `attribute3.value.constraint`
Http Requests Check | org.apache.felix.hc.generalchecks.HttpRequestsCheck | yes | Allows to check a list of URLs against response code, response headers, timing, response content (plain content via RegEx or JSON via path expression). See [Request Spec Syntax](#request-spec-syntax)
Scripted Check | org.apache.felix.hc.generalchecks.ScriptedHealthCheck | yes | Allows to run an arbitrary script. To configure use either `script` to provide a script directly or `scriptUrl` to link to an external script (may be a file URL or a link to a JCR file if a Sling Repository exists, e.g. `jcr:/etc/hc/check1.groovy`). Use the `language` property to refer to a registered script engine (e.g. install bundle `groovy-all` to be able to use language `groovy`). The script has the bindings `log`, `scriptHelper` and `bundleContext`. `log` is an instance of `org.apache.felix.hc.api.FormattingResultLog` and is used to define the result of the HC. `scriptHelper.getService(classObj)` can be used as shortcut to retrieve a service. `scriptHelper.getServices(classObj, filter)` can be used to retrieve multiple services for a class using given filter. For all services retrieved via scriptHelper, `ungetService(...)` is called automatically at the end of the script execution. If a Sling repository is available, the bindings `resourceResolver` and `session` are available automatically (for this case a serivce user mapping for `org.apache.felix.healthcheck.generalchecks:scripted` is required). The script does not need to return any value, but if it does and it is a `org.apache.felix.hc.api.Result`, that result and entries in `log` are combined.

### Constraints

The `JMX Attribute Check` and `Http Requests Check` allow to check values against contraints. See the following examples:

* value `string value` (checks for equality)
* value ` = 0`
* value ` > 0`
* value ` < 100`
* value ` BETWEEN 3 AND 7`
* value ` CONTAINS a string to find in value` (searches for `a string to find in value` in value)
* value ` STARTS_WITH prefix string` (checks for prefix `prefix string`)
* value ` ENDS_WITH suffixStr` (checks for suffix `suffixStr `)
* value ` MATCHES ^.*SomeRegEx[0-9]$` (checks for the given RegEx)
* value ` OLDER_THAN 100 ms` (checks a time value to be older than given value, other units `s`, `min`, `h`, `days` are supported as well, e.g. ` OLDER_THAN 10 days`)
* `NOT` prefix works for all expressions, e.g. `NOT 20`, `NOT > 20`, `NOT BETWEEN 3 AND 7`, `NOT MATCHES ^.*SomeRegEx[0-9]$`

Also see class `org.apache.felix.hc.generalchecks.util.SimpleConstraintsChecker` and its JUnit test.

### Request Spec Syntax

The `Http Requests Check` allows to configure a list of request specs. Requests specs have two parts: Before `=>` can be a simple URL/path with curl-syntax advanced options (e.g. setting a header with `-H "Test: Test val"`), after the `=>` it is a simple response code that can be followed ` && MATCHES <RegEx>` to match the response entity against or other matchers like HEADER, TIME or JSON.

Examples:

* `/path/example.html`: assumes 200 for the request to localhost:<defaultPort>
* `http://www.example.com/path/example.html => 200`: explicitly checking 200 response code for full URL
* `/protected/example.html => 401`: protected page
* `-u admin:admin /protected/example.html => 200`: protected page with password (only use for non-sensitive credentials)
* `/path/example.html => 200 && MATCHES <title>html title.*</title>`: ensure 200 response and matching content
* `/path/example.html => 200 && HEADER Content-Type MATCHES text/html.*`: Checks for content type
* `/path/example.json => 200 && JSON [3].prop = myval`: checks JSON response's third element for property `prop` to equal to `myval`
* `-H "Test: Test val" /path/example.json => 200 && JSON .city STARTS_WITH New`: checks JSON response's `city` property to start with `New`
* `/path/example-timing-important.html => 200 && TIME < 5 ms`: Checks if the response time is smaller than specified

All constraints from [Constraints](#constraints) can be used.

### Adjustable Status Health Check

This is a health check that can be dynamically controlled via JMX bean `org.apache.felix.healthcheck:type=AdjustableStatusHealthCheck`. It allows to dynamically add a health check that returns `WARN` (operation `addWarnResultForTags(String)`), `CRITICAL` (operation `addCriticalResultForTags(String)`) or `TEMPORARILY_UNAVAILABLE` (operation `addTemporarilyUnavailableResultForTags(String)`) for certain tags. This is useful for testing purposes or go-live sequences. The operation `reset()` removes the dynamic result again.

## Executing Health Checks

Health Checks can be executed via a [webconsole plugin](#webconsole-plugin), the [health check servlet](#health-check-servlet) or via [JMX](#jmx-access-to-health-checks). `HealthCheck` services can be selected for execution based on their `hc.tags` multi-value service property.

The `HealthCheckFilter` utility accepts positive and negative tag parameters, so that `osgi,-security`
selects all `HealthCheck` having the `osgi` tag but not the `security` tag, for example.

For advanced use cases it is also possible to use the API directly by using the interface `org.apache.felix.hc.api.execution.HealthCheckExecutor`.

### Configuring the Health Check Executor

The health check executor can **optionally** be configured via service PID `org.apache.felix.hc.core.impl.executor.HealthCheckExecutorImpl`:

Property    | Type     | Default | Description
----------- | -------- | ------ | ------------
`timeoutInMs`    | Long   | 2000ms | Timeout in ms until a check is marked as timed out
`longRunningFutureThresholdForCriticalMs` | Long | 300000ms (5min) | Threshold in ms until a check is marked as 'exceedingly' timed out and will marked CRITICAL instead of WARN only
`resultCacheTtlInMs` | Long | 2000ms | Result Cache time to live - results will be cached for the given time
`temporarilyAvailableGracePeriodInMs` | Long | 60000ms (10min) | After this configured period, health checks continously reporting `TEMPORARILY_UNAVAILABLE` are automatically turned into status `CRITICAL`
`autoLogging` | Boolean | false | If enabled, will automatically log entries of ResultLog (or FormattingResultLog resp.) using Log4j. The logging category used is the class instantiating ResultLog prefixed with 'healthchecks.', for instance 'healthchecks.com.mycorp.myplatform.mymodule.ModuleCheck'. The prefix allows for easy configuration of a log file containing all health check results.

### JMX access to health checks

Health checks that define the service property `hc.mbean.name` will automatically get the JMX bean with that name, the domain `org.apache.felix.healthcheck` and with the type `HealthCheck` registered. The bean provides access to the `Result` (status, logs, etc.)

### Health Check Servlet

The health check servlet allows to query the checks via http. It provides
similar features to the Web Console plugin described above, with output in HTML, JSON (plain or jsonp) and TXT (concise or verbose) formats (see HTML format rendering page for more documentation).

The Health Checks Servlet is disabled by default, to enable it create an OSGi configuration like

    FACTORY_PID = package org.apache.felix.hc.core.impl.servlet.HealthCheckExecutorServlet
    servletPath = /system/health
    servletContextName = org.osgi.service.http

which specifies the servlet's base path and the servlet context to use. That URL then returns an HTML page, by default with the results of all active health checks and
with instructions at the end of the page about URL parameters which can be used to select specific Health Checks and control their execution and output format.

Note that by design **the Health Checks Servlet doesn't do any access control by itself** to ensure it can detect unhealthy states of the authentication itself. Make sure the configured path is only accessible to relevant infrastructure and operations people. Usually all `/system/*` paths are only accessible from a local network and not routed to the Internet.

By default the HC servlet sends the CORS header `Access-Control-Allow-Origin: *` to allow for client-side browser integrations. The behaviour can be configured using the OSGi config property `cors.accessControlAllowOrigin` (a blank value disables the header).

### Webconsole plugin

If the `org.apache.felix.hc.webconsole` bundle is installed, a webconsole plugin
at `/system/console/healthcheck` allows for executing health checks, optionally selected
based on their tags (positive and negative selection, see the `HealthCheckFilter` mention above).

The DEBUG logs of health checks can optionally be displayed, and an option allows for showing only health checks that have a non-OK status.

### Gogo Console

The Gogo command `hc:exec` can be used as follows:

    hc:exec [-v] [-a] tag1,tag2
      -v verbose/debug
      -a combine tags with and logic (instead of or logic)

The command is available without installing additional bundles (it is included in the core bundle `org.apache.felix.healthcheck.core`)

## Monitoring Health Checks

### Setting up a monitor configuration

By default, health checks are only executed if explicitly triggered via one of the mechanisms as described in [Executing Health Checks](#executing-health-checks) (servlet, web console plugin, JMX, executor API). With the `HealthCheckMonitor`, Health checks can be regularly monitored by configuring the the **factory PID** `org.apache.felix.hc.core.impl.monitor.HealthCheckMonitor` with the following properties:

Property    | Type     | Default | Description
----------- | -------- | ------ | ------------
`tags` and/or `names` | String[] | none, at least one of the two is required | **Will regularly call all given tags and/or names**. All given tags/names are executed in parallel. If the set of tags/names include some checks multiple times it does not matter, the `HealthCheckExecutor` will always ensure checks are executed once at a time only.
`intervalInSec` or `cronExpression` | Long or String (cron) | none, one of the two is required | The interval in which the given tags/names will be executed
`registerHealthyMarkerService` | boolean | true | For the case a given tag/name is healthy, will register a service `org.apache.felix.hc.api.condition.Healthy` with property tag=<tagname> (or name=<hc.name>) that other services can depend on. For the special case of the tag `systemready`, the marker service `org.apache.felix.hc.api.condition.SystemReady` is registered
`registerUnhealthyMarkerService` | boolean | false | For the case a given tag/name is **un**healthy, will register a service `org.apache.felix.hc.api.condition.Unhealthy` with property tag=<tagname> (or name=<hc.name>) that other services can depend on
`treatWarnAsHealthy` | boolean | true | `WARN` usually means [the system is usable](#semantic-meaning-of-health-check-results), hence WARN is treated as healthy by default. When set to false `WARN` is treated as `Unhealthy`
`sendEvents` | enum `NONE`, `STATUS_CHANGES`, `STATUS_CHANGES_OR_NOT_OK` or `ALL` | `STATUS_CHANGES` | Whether to send events for health check status changes. See [below](#osgi-events-for-health-check-status-changes-and-updates) for details.
`logResults` | enum `NONE`, `STATUS_CHANGES`, `STATUS_CHANGES_OR_NOT_OK` or `ALL` | `NONE ` | Whether to log the result of the monitor to the regular log file
`logAllResultsAsInfo` | boolean | false | If `logResults` is enabled and this is enabled, all results will be logged with INFO log level. Otherwise WARN and INFO are used depending on the health state.
`isDynamic` | boolean | false | In dynamic mode all checks for names/tags are monitored individually (this means events are sent/services registered for name only, never for given tags). This mode allows to use `*` in tags to query for all health checks in system. It is also possible to query for all except certain tags by using `-`, e.g. by configuring the values `*`, `-tag1` and `-tag2` for `tags`.

### OSGi Condition to depend on a health status in

It is possible to use [OSGi Conditions](https://docs.osgi.org/specification/osgi.core/8.0.0/service.condition.html) to depend on the health status of a certain tag or name. For that to work, a `HealthCheckMonitor` needs to be configured for the relevant tag or name. An OSGi Condition service is registered using the tag or name prefixed by `felix.hc.`.

For example, to depend on a health status in a Declarative Service component, use `@SatisfyingConditionTarget`. The below will only activate the component on healthiness of a certain tag/name:

```
@Component
@SatisfyingConditionTarget("(osgi.condition.id=felix.hc.dbavail)")
public class MyComponent {
   ...
}
```

It is also possible to use a Condition in a reference and later on in the code figure out if healthiness is reached:

```
@Component
public class MyComponent {

    @Reference(target="(osgi.condition.id=felix.hc.dbavail)", policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.OPTIONAL)
    volatile Condition healthy;

    public void mymethod() {
        if (healthy == null) {
            // not healthy
        } else {
            // healthy
        }
    }
}
```


### Marker Service to depend on a health status in Declarative Service Components

It is possible to use OSGi service references to depend on the health status of a certain tag or name. For that to work, a `HealthCheckMonitor` needs to be configured for the relevant tag or name. To depend on a health status in a component, use a `@Reference` to one of the marker services `Healthy`, `Unhealthy` and `SystemReady` - this will then automatically activate/deactivate the component based on the certain health status. To activate a component only upon healthiness of a certain tag/name use the following code:

```
   @Reference(target="(tag=dbavail)")
   Healthy healthy;

   @Reference(target="(name=My Health Check)")
   Healthy healthy;
```
For the special tag `systemready`, there is a convenience marker interface available:

```
   @Reference
   SystemReady systemReady;
```
It is also possible to depend on a unhealthy state (e.g. for fallback functionality or self-healing):

```
   @Reference(target="(tag=dbavail)")
   Unhealthy unhealthy;
```


### OSGi events for Health Check status changes and updates

OSGi events with topic `org/apache/felix/health/*` are sent for tags/names that a `HealthCheckMonitor` is configured for and if `sendEvents` is set to `STATUS_CHANGES` or `ALL`:

* `STATUS_CHANGES` notifies only of status changes with suffix `/STATUS_CHANGED`
* `ALL` sends events whenever the monitor runs, depending on status will either send the event with suffix `/UPDATED` or `/STATUS_CHANGED`

All events sent generally carry the properties `executionResult`, `status` and `previousStatus`.

| Example | Description
------- | -----
`org/apache/felix/health/tag/mytag/STATUS_CHANGED` | Status for tag `mytag` has changed compared to last execution
`org/apache/felix/health/tag/My_HC_Name/UPDATED ` (spaces in names are replaced with underscores to ensure valid topic names) | Status for name `My HC Name` has not changed but HC was executed and execution result is available in event property `executionResult`.
`org/apache/felix/health/component/com/myprj/MyHealthCheck/UPDATED` (`.` are replaced with slashes to produce valid topic names) | HC based on SCR component `com.myprj.MyHealthCheck` was executed without having the status changed. The SCR component event is sent in addition to the name event

Event listener example:

```
@Component(property = { EventConstants.EVENT_TOPIC + "=org/apache/felix/health/*"})
public class HealthEventHandler implements EventHandler {
    private static final Logger LOG = LoggerFactory.getLogger(HealthEventHandler.class);

    public void handleEvent(Event event) {
        LOG.info("Received event: "+event.getTopic());
        LOG.info("    previousStatus:  "+event.getProperty("previousStatus"));
        LOG.info("    status:  "+event.getProperty("status"));
        HealthCheckExecutionResult executionResult = (HealthCheckExecutionResult) event.getProperty("executionResult");
        LOG.info("    executionResult:  "+executionResult);
        LOG.info("    result:  "+executionResult.getHealthCheckResult());
    }
}
```

## Servlet Filters

### Service Unavailable Filter

For health states of the system that mean that requests can only fail it is possible to configure a Service Unavailable Filter that will cut off all requests if certain tags are in a `CRITICAL` or `TEMPORARILY_UNAVAILABLE` status. Typical usecases are startup/shutdown and deployments. Other scenarios include maintenance processes that require request processing of certain servlets to be stalled (the filter can be configured to be active on arbitrary paths). It is possible to configure a custom response text/html.

Configure the factory configuration with PID
`org.apache.felix.hc.core.impl.filter.ServiceUnavailableFilter` with specific parameters to activate the Service Unavailable Filter:

| Name | Default/Required | Description |
| --- | --- | --- |
| `osgi.http.whiteboard.filter.regex` | required | Regex path on where the filter is active, e.g. `(?!/system/).*` or `.*`. See Http Whiteboard documentation[^1] and hint[^2] |
| `osgi.http.whiteboard.context.select` | required | OSGi service filter for selecting relevant contexts, e.g. `(osgi.http.whiteboard.context.name=*)` selects all contexts. See Http Whiteboard documentation[^1] and hint[^2] |
| `tags` | required | List of tags to query the status in order to decide if it is 503 or not  |
| `statusFor503 ` | default `TEMPORARILY_UNAVAILABLE` | First status that causes a 503 response. The default `TEMPORARILY_UNAVAILABLE` will not send 503 for `OK` and `WARN` but for `TEMPORARILY_UNAVAILABLE`, `CRITICAL` and `HEALTH_CHECK_ERROR` |
| `includeExecutionResult ` | `false` | Will include the execution result in the response (as html comment for html case, otherwise as text). |
| `responseTextFor503 ` | required | Response text for 503 responses. Value can be either the content directly (e.g. just the string `Service Unavailable`) or in the format `classpath:<symbolic-bundle-id>:/path/to/file.html` (it uses `Bundle.getEntry()` to retrieve the file). The response content type is auto-detected to either `text/html` or `text/plain`.  |
| `autoDisableFilter ` | default `false` | If true, will automatically disable the filter once the filter continued the filter chain without 503 for the first time. The filter will be automatically enabled again if the start level of the framework changes (hence on shutdown it will be active again). Useful for server startup scenarios.|
| `avoid404DuringStartup` | default `false` | If true, will automatically register a dummy servlet to ensure this filter becomes effective (complex applications might have the http whiteboard active but no servlets be active during early phases of startup, a filter only ever becomes active if there is a servlet registered). Useful for server startup scenarios. |
| `service.ranking` | default `Integer.MAX_VALUE` (first in filter chain) | The `service.ranking` for the filter as respected by http whiteboard[^1]. |

### Adding ad hoc results during request processing

For certain scenarios it is useful to add a health check dynamically for a specific tag during request processing, e.g. it can be useful during deployment requests (the tag(s) being added can be queried by e.g. load balancer or Service Unavailable Filter.

To achieve this configure the factory configuration with PID
`org.apache.felix.hc.core.impl.filter.AdhocResultDuringRequestProcessingFilter` with specific parameters:

| Name | Default/Required | Description |
| --- | --- | --- |
| `osgi.http.whiteboard.filter.regex` | required | Regex path on where the filter is active, e.g. `(?!/system/).*` or `.*`. See Http Whiteboard documentation |
| `osgi.http.whiteboard.context.select` | required | OSGi service filter for selecting relevant contexts, e.g. `(osgi.http.whiteboard.context.name=*)` selects all contexts. See Http Whiteboard |
| `service.ranking` | default `0` | The `service.ranking` for the filter as respected by http whiteboard[^1]. |
| `method` | default restriction not active | Relevant request method (leave empty to not restrict to a method) |
| `userAgentRegEx` | default restriction not active | Relevant user agent header (leave emtpy to not restrict to a user agent) |
| `hcName` | required | Name of health check during request processing  |
| `tags` | required | List of tags the adhoc result shall be registered for (tags are not active during configured delay in case 'delayProcessingInSec' is configured) |
| `statusDuringRequestProcessing` | default `TEMPORARILY_UNAVAILABLE` | Status to be sent during request processing |
| `delayProcessingInSec` | default `0` (not active) | Time to delay processing of request in sec (the default 0 turns the delay off). Use together with 'tagsDuringDelayedProcessing' advertise request processing before actual action (e.g. to signal a deployment request to a periodically querying load balancer before deployment starts) |
| `tagsDuringDelayedProcessing` | default `0` (not active) | List of tags the adhoc result is be registered also during waiting for the configured delay |
| `waitAfterProcessing.forTags` | default empty (not active) | List of tags to be waited for after processing (leave empty to not wait). While waiting the tags from property `tags` remain in configured state. |
| `waitAfterProcessing.initialWait` | 3 sec  | Initial waiting time in sec until 'waitAfterProcessing.forTags' are checked for the first time. |
| `waitAfterProcessing.maxDelay` | 120 sec | Maximum delay in sec that can be caused when 'waitAfterProcessing.forTags' is configured (waiting is aborted after that time) |

[^1]: [https://felix.apache.org/documentation/subprojects/apache-felix-http-service.html#filter-service-properties](https://felix.apache.org/documentation/subprojects/apache-felix-http-service.html#filter-service-properties)
[^2]: Choose a combination of `osgi.http.whiteboard.filter.regex`/ `osgi.http.whiteboard.context.select` wisely, e.g. `osgi.http.whiteboard.context.select=(osgi.http.whiteboard.context.name=*)` and `osgi.http.whiteboard.filter.regex=.*` would also cut off all admin paths.