# tout — just recipes

default:
    @just --list

# generate gradle wrapper (needs network once)
wrapper:
    /tmp/gradle-8.10.2/bin/gradle wrapper --gradle-version 8.10.2

build:
    ./gradlew :app:assembleDebug

test:
    ./gradlew :app:testDebugUnitTest

install:
    ./gradlew :app:installDebug

clean:
    ./gradlew clean

# tail on-device log for the app
log:
    /Users/nik/Library/Android/sdk/platform-tools/adb logcat --pid=$(/Users/nik/Library/Android/sdk/platform-tools/adb shell pidof com.tout.app)

uninstall:
    /Users/nik/Library/Android/sdk/platform-tools/adb uninstall com.tout.app
