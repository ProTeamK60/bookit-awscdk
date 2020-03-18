package se.knowit.bookit;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.iam.IManagedPolicy;
import software.amazon.awscdk.services.iam.IRole;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class BookitKafkaStack extends Stack {
    
    private interface BookitKafkaStackConfig extends Config {
        @Key("kafka.aws.ec2.instanceclass")
        @DefaultValue("BURSTABLE2")
        InstanceClass ec2InstanceClass();
        
        @Key("kafka.aws.ec2.instancesize")
        @DefaultValue("SMALL")
        InstanceSize ec2InstanceSize();
        
        @Key("kafka.aws.ec2.ami.id")
        @DefaultValue("ami-0e8c04af2729ff1bb")
        String kafkaAmiId();
        
        @Key("kafka.ssh.publicKey.name")
        String keyName();
        
        @Key("kafka.aws.securitygroupname")
        @DefaultValue("kafka-sg")
        String securityGroupName();
        
        @Key("kafka.aws.ec2.rootvolume.size.gb")
        @DefaultValue("8")
        int rootSize();
        
        @Key("kafka.aws.ec2.datavolume.size.gb")
        @DefaultValue("2")
        int dataSize();
    }
    private final int kafkaSubnetCount;
    private final IRole awsec2ROLE;
    private final BookitNamespaceStack.BookitNamespaceStackConfig namespaceStackConfig;
    private final GenericLinuxImage linuxImage;
    private final List<Instance> instances;
    private final InstanceType instanceType;
    private final SecurityGroup kafkaSecurityGroup;
    
    private final BookitCommonStack commonStack;
    private final BookitKafkaStackConfig config;
    private final SubnetSelection kafkaSubnetSelection;
    
    private final AtomicInteger instanceCounter;
    
    public BookitKafkaStack(Construct scope, String id, BookitCommonStack commonStack, StackProps props, String bastionPrivateIp) {
        super(scope, id, props);
        instanceCounter = new AtomicInteger(1);
        this.commonStack = commonStack;
        this.getNode().addDependency(this.commonStack);
        config = ConfigFactory.create(BookitKafkaStackConfig.class);
        namespaceStackConfig = ConfigFactory.create(BookitNamespaceStack.BookitNamespaceStackConfig.class);
        IManagedPolicy awsCloudMapDiscoverInstanceAccess = ManagedPolicy.fromAwsManagedPolicyName("AWSCloudMapDiscoverInstanceAccess");
        
        instanceType = InstanceType.of(config.ec2InstanceClass(), config.ec2InstanceSize());
        kafkaSecurityGroup = getKafkaSecurityGroup(commonStack.getVpc(), String.format("%s/32", bastionPrivateIp));
        linuxImage = GenericLinuxImage.Builder
                .create(Collections.singletonMap(commonStack.getRegion(), config.kafkaAmiId())).build();
        kafkaSubnetSelection = SubnetSelection.builder().subnetType(SubnetType.PRIVATE).build();
        List<ISubnet> privateSubnets = commonStack.getVpc().getPrivateSubnets();
        kafkaSubnetCount = privateSubnets.size();
        awsec2ROLE = Role.fromRoleArn(this, "AWSEC2ROLE", "arn:aws:iam::194896174337:role/AmazonEC2Role_CWAgent");
        awsec2ROLE.addManagedPolicy(awsCloudMapDiscoverInstanceAccess);
        instances = privateSubnets.stream()
                .map(ISubnet::getAvailabilityZone)
                .sorted()
                .map(this::constructInstanceForZone)
                .collect(Collectors.toList());
    }
    
    private Instance constructInstanceForZone(String zone) {
        List<BlockDevice> blockDevices = getBlockDevices();
        UserData userData = UserData.forLinux();
        userData.addCommands("exec 5> >(logger -t $0)");
        userData.addCommands("BASH_XTRACEFD=\"5\"");
        userData.addCommands("PS4='$LINENO: '");
        userData.addCommands("set -x");
        userData.addCommands("export INSTANCE_NUMBER=" + instanceCounter.get());
        userData.addCommands("export TOTAL_INSTANCES=" + kafkaSubnetCount);
        userData.addCommands("export NAMESPACE=" + namespaceStackConfig.namespaceName());
        userData.addCommands("export DISCOVERY_SERVICE_NAME=" + namespaceStackConfig.kafkaDiscoveryServiceName());
        addUserData(userData);
        return Instance.Builder
                .create(this, "kafka_" + zone)
                .vpc(commonStack.getVpc())
                .keyName(config.keyName())
                .instanceType(instanceType)
                .machineImage(linuxImage)
                .availabilityZone(zone)
                .vpcSubnets(kafkaSubnetSelection)
                .instanceName("Kafka_" + instanceCounter.getAndIncrement())
                .securityGroup(kafkaSecurityGroup)
                .blockDevices(blockDevices)
                .role(awsec2ROLE)
                .userData(userData)
                .build();
    }
    
    private void addUserData(UserData userData) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream("/userdata.sh")))) {
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                userData.addCommands(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private List<BlockDevice> getBlockDevices() {
        BlockDevice root = BlockDevice.builder()
                .deviceName("/dev/xvda")
                .volume(BlockDeviceVolume.ebs(Math.max(8, config.rootSize())))
                .mappingEnabled(true)
                .build();
        BlockDevice data = BlockDevice.builder()
                .deviceName("/dev/xvdb")
                .volume(BlockDeviceVolume.ebs(Math.max(2, config.dataSize())))
                .mappingEnabled(true)
                .build();
        return Arrays.asList(root, data);
    }
    
    public Map<String, String> getKafkaInstanceDetails() {
        Map<String, String> result = new HashMap<>();
        instances.forEach(instance -> result.put(instance.getInstanceId(), instance.getInstancePrivateIp()));
        return result;
    }
    
    private SecurityGroup getKafkaSecurityGroup(Vpc vpc, String sshAccessCidr) {
        SecurityGroup kafka_security_group = SecurityGroup.Builder
                .create(this, "kafka-sg")
                .vpc(vpc)
                .description("Kafka security group")
                .securityGroupName(config.securityGroupName())
                .build();
        IPeer vpcCidr = Peer.ipv4(vpc.getVpcCidrBlock());
        kafka_security_group.addIngressRule(Peer.ipv4(sshAccessCidr), Port.tcp(22));
        kafka_security_group.addIngressRule(vpcCidr, Port.tcp(9092));
        kafka_security_group.addIngressRule(vpcCidr, Port.tcp(2181));
        kafka_security_group.addIngressRule(vpcCidr, Port.tcp(2888));
        kafka_security_group.addIngressRule(vpcCidr, Port.tcp(3888));
        return kafka_security_group;
    }
}
