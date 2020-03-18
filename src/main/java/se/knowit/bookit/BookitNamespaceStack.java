package se.knowit.bookit;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.servicediscovery.*;

import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

public class BookitNamespaceStack extends Stack {
    
    interface BookitNamespaceStackConfig extends Config {
        String namespaceName();
    
        @Key("kafka.discoveryservice.name")
        @DefaultValue("kafka")
        String kafkaDiscoveryServiceName();
        
        @Key("kafka.discoveryservice.ttl.minutes")
        @DefaultValue("5")
        Integer kafkaDiscoveryServiceTTL();
    }
    private final PrivateDnsNamespace dnsNamespace;
    private final Service kafkaDiscoveryService;
    private final AtomicInteger ID;

    public BookitNamespaceStack(final Construct scope, final String id, final BookitCommonStack bookitCommonStack, final StackProps props, final BookitKafkaStack bookitKafkaStack)  {
        super(scope, id, props);
        ID = new AtomicInteger(1);
        this.getNode().addDependency(bookitCommonStack);
        this.getNode().addDependency(bookitKafkaStack);
        BookitNamespaceStackConfig config = ConfigFactory.create(BookitNamespaceStackConfig.class);
        
        dnsNamespace = PrivateDnsNamespace.Builder
                .create(this, "dnsNamespace")
                .vpc(bookitCommonStack.getVpc())
                .name(config.namespaceName())
                .build();
        kafkaDiscoveryService = dnsNamespace
                .createService("kafkaDiscovery", DnsServiceProps.builder()
                        .description("Kafka brokers")
                        .name(config.kafkaDiscoveryServiceName())
                        .dnsRecordType(DnsRecordType.A)
                        .routingPolicy(RoutingPolicy.MULTIVALUE)
                        .dnsTtl(Duration.minutes(config.kafkaDiscoveryServiceTTL()))
                        .build());
        bookitKafkaStack.getKafkaInstanceDetails().entrySet().stream().forEach(this::registerKafkaInstanceInNamespace);
    }
    
    private void registerKafkaInstanceInNamespace(Entry<String, String> kafkaInfo){
        kafkaDiscoveryService.registerIpInstance("kafka_" + ID.get(), IpInstanceBaseProps.builder()
                .ipv4(kafkaInfo.getValue())
                .port(9092)
                .instanceId("kafka_" + ID.getAndIncrement() + "_" + kafkaInfo.getKey())
                .build());
    }
    
    public PrivateDnsNamespace getDnsNamespace() {
        return dnsNamespace;
    }
    
    public Service getKafkaDiscoveryService() {
        return kafkaDiscoveryService;
    }
}
