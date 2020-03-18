package se.knowit.bookit;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;

public class BookitApp {
    @Config.LoadPolicy(Config.LoadType.MERGE)
    /*
    * system property overrides environment property which overrides
    * se/knowit/bookit/BookitApp$BookitAppConfig.properties
    * */
    @Config.Sources({"system:properties", "system:env"})
    private interface BookitAppConfig extends Config {
        @Key("aws.accountId")
        String awsAccountId();
        
        @Key("aws.region")
        String awsRegion();
    }
    public static void main(final String[] args) {
        App app = new App();
        BookitAppConfig config = ConfigFactory.create(BookitAppConfig.class);
        StackProps stackProps = StackProps.builder().env(
                Environment.builder()
                        .account(config.awsAccountId())
                        .region(config.awsRegion())
                        .build()).build();
        BookitCommonStack bookitCommonStack = new BookitCommonStack(app, "BookitCommonStack", stackProps);
        BookitBastionStack bookitBastionStack = new BookitBastionStack(app, "BookitBastionStack",
                bookitCommonStack, stackProps);
        BookitKafkaStack bookitKafkaStack = new BookitKafkaStack(app, "BookitKafkaStack",
                bookitCommonStack, stackProps, bookitBastionStack.getPrivateIp());
        BookitNamespaceStack bookitNamespaceStack = new BookitNamespaceStack(app, "BookitNamespaceStack",
                bookitCommonStack, stackProps, bookitKafkaStack);
        app.synth();
    }
}
