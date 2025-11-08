# bash
 mvn install
 sudo systemctl stop tomcat
 yes | sudo cp -rf mondrian/target/mondrian-*.jar /opt/tomcat/webapps/emondrian/WEB-INF/lib/ 
 sudo rm -f rm -f /opt/tomcat/logs/*.*
sudo systemctl start tomcat 

