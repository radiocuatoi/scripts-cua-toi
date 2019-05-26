def buildCredentials = System.env["BUILD_CREDENTIALS"]
buildCredentials = buildCredentials != null ? buildCredentials : System.properties["cuatoi.buildCredentials"]

println '''To enable Heroku support:
- add heroku([[appName: "APP_NAME_1", email  : "EMAIL_1", apiKey : "API_KEY_1"],
              [appName: "APP_NAME_2", email  : "EMAIL_2", apiKey : "API_KEY_2"]])
'''
ext.heroku = { List<Map> apps ->
    task herokuDeploy(type: Exec, dependsOn: 'build') {
        group = 'cuatoi'
        doFirst {
            new File("$buildDir/deploy.sh").text = '''
set -e
echo "Deploying $APP_NAME using $EMAIL"
echo "" > ~/.netrc
echo "machine api.heroku.com" >> ~/.netrc
echo "  login $EMAIL" >> ~/.netrc
echo "  password $API_KEY" >> ~/.netrc
echo "machine git.heroku.com" >> ~/.netrc
echo "  login $EMAIL" >> ~/.netrc
echo "  password $API_KEY" >> ~/.netrc
git config --global user.email "$EMAIL"
git config --global user.name "$APP_NAME"
git config --global push.default simple

heroku repo:reset -a $APP_NAME

rm -rf /git/
mkdir /git/
cd /git/
git init
heroku git:remote -a $APP_NAME 
rm -rf *

cp /staging/caddy ./
cp /staging/build/pom.xml ./
cp /staging/build/Caddyfile ./
cp /staging/build/run.sh ./
cp /staging/build/start.sh ./
cp /staging/build/libs/* ./
echo 'web: ./start.sh' > ./Procfile
echo "export HEROKU_APP_NAME=$APP_NAME" > ./setenv.sh
chmod +x ./setenv.sh
echo $(date) > stamp.txt

git add -A .
git commit -m "OK $(date)"
git push heroku master -f
'''
            new File("$buildDir/pom.xml").text = '''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>groupId</groupId>
    <artifactId>artifactid</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>
    <name>projectName</name>
</project>
'''
            new File("$buildDir/Caddyfile").text = '''
0.0.0.0
tls off
proxy / localhost:8080 {
    transparent
    websocket
}
'''
            new File("$buildDir/run.sh").text = '''
set -e
export APP_PORT=8080
java -Xss512k -Xms64m -Xmx256m -server\\
    -Duser.timezone=Asia/Ho_Chi_Minh \\
    -Dspring.profiles.active=default,prod \\
    -jar *.jar
'''
            new File("$buildDir/start.sh").text = '''
set -e
echo "Starting heroku-deployer "
if [ -z "$PORT" ]; then
    echo "PORT parameter not found, setting to 8081"
    export PORT=8081
fi

echo "Caddy Port is $PORT"
./caddy -conf ./Caddyfile -port ${PORT} &
echo "Caddy started."
. ./setenv.sh
echo "HEROKU_APP_NAME=$HEROKU_APP_NAME"
echo "Starting app..."
./run.sh
'''

            def deployCmd = ''
            apps.forEach { app ->
                deployCmd += "RUN EMAIL=${app.email} APP_NAME=${app.appName} API_KEY=${app.apiKey} ./build/deploy.sh\n"
            }
            new File("$buildDir/Dockerfile").text = '''FROM abiosoft/caddy
WORKDIR /staging/
#prepare cli and static
RUN apk --no-cache add nodejs npm unzip
RUN npm install -g heroku
RUN heroku plugins:install heroku-repo
RUN cp /usr/bin/caddy /staging/caddy
#prepare heroku app
ADD ./ /staging/
RUN chmod +x build/*.sh
''' + deployCmd
        }
        commandLine 'docker', "build", "--pull", "-t", "$rootProject.name:dev", "-f", "$buildDir/Dockerfile", "."
    }
}