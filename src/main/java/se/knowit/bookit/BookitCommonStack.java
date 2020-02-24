package se.knowit.bookit;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.RemovalPolicy;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.servicediscovery.PrivateDnsNamespace;

public class BookitCommonStack extends Stack {
    private interface BookitCommonStackConfig extends Config {
        @Key("vpc.maxAzs")
        @DefaultValue("3")
        int maxAzs();
        
        String namespaceName();
        
        @DefaultValue("bookit-test-logs")
        String logGroupName();
        
        @DefaultValue("DESTROY")
        RemovalPolicy logRemovalPolicy();
        
        @DefaultValue("TWO_WEEKS")
        RetentionDays logRetentionTime();
    }
    private final Vpc vpc;
    private final PrivateDnsNamespace dnsNamespace;
    private final LogGroup logGroup;
    
    public BookitCommonStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public BookitCommonStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);
        BookitCommonStackConfig config = ConfigFactory.create(BookitCommonStackConfig.class);
        // The code that defines your stack goes here
        vpc = Vpc.Builder.create(this, "vpc")
                .maxAzs(config.maxAzs())  // Default is all AZs in region
                .build();
        dnsNamespace = PrivateDnsNamespace.Builder
                .create(this, "dnsNamespace")
                .vpc(vpc)
                .name(config.namespaceName())
                .build();
        logGroup = LogGroup.Builder
                .create(this, "logGroup")
                .logGroupName(config.logGroupName())
                .removalPolicy(config.logRemovalPolicy())
                .retention(config.logRetentionTime())
                .build();
    }
    
    public Vpc getVpc() {
        return vpc;
    }
    
    public PrivateDnsNamespace getDnsNamespace() {
        return dnsNamespace;
    }
    
    public LogGroup getLogGroup() {
        return logGroup;
    }
}
