package se.knowit.bookit;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.ec2.*;

import java.util.List;
import java.util.function.Consumer;

public class BookitBastionStack extends Stack {
    private final String publicIp;
    private final String privateIp;
    
    public String getPublicIp() {
        return publicIp;
    }
    
    public String getPrivateIp() {
        return privateIp;
    }
    
    private interface BookitBastionStackConfig extends Config {
        @Key("bastion.ssh.whitelist")
        @DefaultValue("178.174.167.26")//K60 IP
        List<String> sshWhiteList();
        
        @Key("bastion.ssh.publicKey.name")
        String keyName();
    
        @Key("bastion.aws.ec2.instanceclass")
        @DefaultValue("BURSTABLE2")
        InstanceClass ec2InstanceClass();
    
        @Key("bastion.aws.ec2.instancesize")
        @DefaultValue("MICRO")
        InstanceSize ec2InstanceSize();
        
        @Key("bastion.aws.ec2.instancename")
        @DefaultValue("Bastion Host")
        String instanceName();
        
        @Key("bastion.aws.securitygroupname")
        @DefaultValue("bastion-sg")
        String securityGroupName();
    }
    
    public BookitBastionStack(Construct scope, String id, BookitCommonStack bookitCommonStack) {
        this(scope, id, bookitCommonStack, null);
    }
    
    public BookitBastionStack(Construct scope, String id, BookitCommonStack bookitCommonStack, StackProps props) {
        super(scope, id, props);
        BookitBastionStackConfig config = ConfigFactory.create(BookitBastionStackConfig.class);
        SecurityGroup bastionSG = SecurityGroup.Builder
                .create(this, "bastion-sg")
                .vpc(bookitCommonStack.getVpc())
                .description("Bastion security group")
                .securityGroupName(config.securityGroupName())
                .build();
        Consumer<? super IPeer> peerConsumer = peer -> bastionSG.addIngressRule(peer, Port.tcp(22));
        config.sshWhiteList().stream()
                .map(ip -> Peer.ipv4(String.format("%s/32", ip)))
                .forEach( peerConsumer);
        InstanceType instanceType = InstanceType.of(config.ec2InstanceClass(), config.ec2InstanceSize());
        Instance bastion = Instance.Builder
                .create(this, "bastion")
                .vpc(bookitCommonStack.getVpc())
                .instanceName(config.instanceName())
                .machineImage(MachineImage.latestAmazonLinux())
                .keyName(config.keyName())
                .instanceType(instanceType)
                .securityGroup(bastionSG)
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
                .build();
        this.publicIp = bastion.getInstancePublicIp();
        this.privateIp = bastion.getInstancePrivateIp();
    }
}
