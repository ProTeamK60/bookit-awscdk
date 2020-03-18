yum -y install git xfsprogs java-1.8.0-openjdk-devel
yum -y update
mkdir /tmp/git && cd "$_"
git clone https://github.com/ProTeamK60/bookit-kafka-config.git
cd bookit-kafka-config
source setup-instance.sh $(pwd)


