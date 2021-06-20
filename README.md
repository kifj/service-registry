[![Actions Status](https://github.com/kifj/service-registry/workflows/Java%20CI/badge.svg)](https://github.com/kifj/service-registry/actions) ![Licence](https://img.shields.io/github/license/kifj/service-registry) ![Issues](https://img.shields.io/github/issues/kifj/service-registry) ![Stars](https://img.shields.io/github/stars/kifj/service-registry)

# service-registry

A simple etcd base service registry for JBoss Wildfly

## How to register a service

Add the dependency to the deployment and add a property file named service-registry.properties to the class path:

    basePackages=list of packages (comma separated) which are scanned for services

In the wildfly container add system properties 
    
    x1.service.registry.etcd the endpoint of etcd service (Default: http://127.0.0.1:4001) 
    x1.service.registry.prefix the top-level folder in etcd (Default: /x1)
    x1.service.registry.stage if you want to have different stages like development, test, production (Default: local)
    x1.service.registry.registerIp if true, the IP address is registered, if false the FQDN (Default false)

For each service in the application which should be published in the service registry, add annotations to the service like this:

	@Services(services = {
      @Service(technology = Technology.REST, value = "my-service-path", 
      version = "1.0", 
      protocols = { Protocol.HTTP, Protocol.HTTPS })
      })
	
Technologies are REST, SOAP, RMI, JMS, WEBSOCKETS, STOMP, AMQP

Protocols are HTTP, HTTPS, WS, WSS, EJB, AMQP, STOMP\_WS, STOMP\_WSS, JNP

In etcd you will find the entries at the path

    /top-level/technology/interface/version/protcol/stage
    e.g. /x1/rest/x1.stomp.rest.ShareResource/1.4/https/local
    
Each host has 1 entry, each entry consists of line with key=value, as in JAVA property files.
Use the class x1.service.client.Resolver to retrieve and parse entries
