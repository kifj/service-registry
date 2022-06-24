package x1.service.test;

import static x1.service.registry.Protocol.EJB;
import static x1.service.registry.Protocol.STOMP_WS;
import static x1.service.registry.Protocol.STOMP_WSS;
import static x1.service.registry.Technology.JMS;
import static x1.service.registry.Technology.STOMP;

import x1.service.registry.Service;
import x1.service.registry.Services;

@Services(services = {
    @Service(technology = JMS, value = "java:/jms/queue/test", version = ResolverTest.APP_VERSION_MAJOR_MINOR,
        protocols = EJB),
    @Service(technology = STOMP, value = "jms.queue.test", version = ResolverTest.APP_VERSION_MAJOR_MINOR,
        protocols = { STOMP_WS, STOMP_WSS }) })
public class ShareMessageListener {

}
