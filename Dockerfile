FROM gradle:8.6-jdk17

ENV ANDROID_SDK_URL https://dl.google.com/android/repository/commandlinetools-linux-7302050_latest.zip
ENV ANDROID_API_LEVEL android-33
ENV ANDROID_BUILD_TOOLS_VERSION 33.0.0
ENV ANDROID_HOME /usr/local/android-sdk-linux
ENV ANDROID_NDK_VERSION 21.4.7075529
ENV ANDROID_VERSION 33
ENV ANDROID_NDK_HOME ${ANDROID_HOME}/ndk/${ANDROID_NDK_VERSION}/
ENV PATH ${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools

RUN mkdir "$ANDROID_HOME" .android && \
    cd "$ANDROID_HOME" && \
    curl -o sdk.zip $ANDROID_SDK_URL && \
    unzip sdk.zip && \
    rm sdk.zip

RUN yes | ${ANDROID_HOME}/cmdline-tools/bin/sdkmanager --sdk_root=$ANDROID_HOME --licenses
RUN $ANDROID_HOME/cmdline-tools/bin/sdkmanager --sdk_root=$ANDROID_HOME --update
RUN $ANDROID_HOME/cmdline-tools/bin/sdkmanager --sdk_root=$ANDROID_HOME "build-tools;30.0.3" \
    "build-tools;${ANDROID_BUILD_TOOLS_VERSION}" \
    "platforms;android-${ANDROID_VERSION}" \
    "platform-tools" \
    "ndk;$ANDROID_NDK_VERSION"
RUN cp $ANDROID_HOME/build-tools/30.0.3/dx $ANDROID_HOME/build-tools/33.0.0/dx
RUN cp $ANDROID_HOME/build-tools/30.0.3/lib/dx.jar $ANDROID_HOME/build-tools/33.0.0/lib/dx.jar
ENV PATH ${ANDROID_NDK_HOME}:$PATH
ENV PATH ${ANDROID_NDK_HOME}/prebuilt/linux-x86_64/bin/:$PATH

COPY . .

# Pre-build the app to cache libs. The real build will be offline.
# Build alpha instead of standalone because debug.keystore has default password.
RUN mkdir -p /home/source/TMessagesProj/build/outputs/apk && \
    mkdir -p /home/source/TMessagesProj/build/outputs/native-debug-symbols && \
    cp -R /home/source/. /home/gradle && \
    cd /home/gradle && \
    gradle :TMessagesProj_AppStandalone:assembleAfatAlpha

CMD mkdir -p /home/source/TMessagesProj/build/outputs/apk && \
    mkdir -p /home/source/TMessagesProj/build/outputs/native-debug-symbols && \
    cp -R /home/source/. /home/gradle && \
    cd /home/gradle && \
    gradle --offline :TMessagesProj_AppStandalone:assembleAfatStandalone && \
    cp -R /home/gradle/TMessagesProj_AppStandalone/build/outputs/apk/. /home/source/TMessagesProj/build/outputs/apk && \
    chmod -R 777 /home/source/TMessagesProj/build