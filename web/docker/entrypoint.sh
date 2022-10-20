#! /bin/bash

if [ "$TZ" = "" ]
then
   echo "Using default timezone (UTC)"
fi

if [[ -z $LOGGING_CONFIG ]]; then
  echo "Default logback configuration file: /home/appuser/logback-spring.xml"
  export LOGGING_CONFIG=/home/appuser/logback-spring.xml
fi

if [[ -z $LOG_LEVEL ]]; then
  echo "Default LOG_LEVEL = INFO"
  export LOG_LEVEL=INFO
fi

if [[ -z $LOG_LEVEL_FRAMEWORK ]]; then
  echo "Default LOG_LEVEL_FRAMEWORK = INFO"
  export LOG_LEVEL_FRAMEWORK=INFO
fi

if [[ -z $CORRELATION_ID ]]; then
  echo "Default CORRELATION_ID = correlation-id"
  export CORRELATION_ID=x-request-id
fi

# Create keystore
if [[ ! -f $STS_TRUST_CERT ]]; then
  echo "$STS_TRUST_CERT does not exists.1"
  exit 1
fi

if [[ ! -f $STS_CLIENT_CERT ]]; then
  echo "$STS_CLIENT_CERT does not exists.2"
  exit 1
fi

if [[ ! -f $STS_CLIENT_KEY ]]; then
  echo "$STS_CLIENT_KEY does not exists.3"
  exit 1
fi

export STS_STORE_PASSWORD=$(date +%s | sha256sum | base64 | head -c 32 ; echo)
export STS_STORE_P12=/home/appuser/client.p12
export STS_STORE=/home/appuser/client.jks
export STS_ALIAS=client
rm -f $STS_STORE_P12 $STS_STORE

keytool -import -file "$STS_TRUST_CERT" -alias sts-signing -keystore "$STS_STORE" -deststorepass "$STS_STORE_PASSWORD" -noprompt
openssl pkcs12 -export -out $STS_STORE_P12 -inkey "$STS_CLIENT_KEY" -in "$STS_CLIENT_CERT" -name "$STS_ALIAS" -password pass:"$STS_STORE_PASSWORD"
keytool -importkeystore -srckeystore "$STS_STORE_P12" -srcstoretype pkcs12 -srcalias $STS_ALIAS -srcstorepass "$STS_STORE_PASSWORD" -destkeystore $STS_STORE -deststoretype jks -deststorepass "$STS_STORE_PASSWORD" -destalias "$STS_ALIAS"

export STS_PROPERTIES=/home/appuser/sts.properties
echo "org.apache.ws.security.crypto.provider=org.apache.ws.security.components.crypto.Merlin" > "$STS_PROPERTIES"
echo "org.apache.ws.security.crypto.merlin.keystore.type=jks" >> "$STS_PROPERTIES"
echo "org.apache.ws.security.crypto.merlin.keystore.password=$STS_STORE_PASSWORD" >> "$STS_PROPERTIES"
echo "org.apache.ws.security.crypto.merlin.keystore.private.password=$STS_STORE_PASSWORD" >> "$STS_PROPERTIES"
echo "org.apache.ws.security.crypto.merlin.keystore.alias=client" >> "$STS_PROPERTIES"
echo "org.apache.ws.security.crypto.merlin.keystore.file=$STS_STORE" >> "$STS_PROPERTIES"

JAR_FILE=web.jar

echo "Starting service with the following command."
echo "java $JVM_OPTS -jar $JAR_FILE"

# start the application
exec java $JVM_OPTS -jar $JAR_FILE
