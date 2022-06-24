package x1.service.test;

import static x1.service.registry.Protocol.HTTP;
import static x1.service.registry.Protocol.HTTPS;
import static x1.service.registry.Technology.REST;

import x1.service.registry.Service;
import x1.service.registry.Services;

@Services(services = { @Service(technology = REST, value = "/shares",
    version = ResolverTest.APP_VERSION_MAJOR_MINOR, protocols = { HTTP, HTTPS }) })
public class ShareResource {

}
