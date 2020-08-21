# Gradle2Maven
a simple tool to move gradle caches into maven local repo

## usage: 
java11+:
- run `java Gradle2Maven.java` to dry-run(will generate a config.properties)
- check the dry-run log, if no problem, modify auto-geneated config.properties: set `dryRun`=false. if you're using custom gradle cache dir or maven local repo dir, set them in config.properties too.
- run `java Gradle2Maven.java` again to move the caches.
from next time, you can just run `java Gradle2Maven.java` once because there is already a config.properties.

java8~java10:
- compile at first: run `javac Gradle2Maven.java` to generate Gradle2Maven.class
- run `java Gradle2Maven.class` to dry-run(will generate a config.properties)
- check the dry-run log, if no problem, modify auto-geneated `config.properties`: set `dryRun=false`. if you're using custom gradle cache dir or maven local repo dir, set them in config.properties too.
- run `java Gradle2Maven.class` again to move the caches.
from next time, you can just run `java Gradle2Maven.class` once because there is already a config.properties.

## default config
default config values in `config.properties` 

`gradleCachePath`: `$GRADLE_USER_HOME/caches/modules-2/files-2.1`(or `$USER_HOME/.gradle/caches/modules-2/files-2.1` if `$GRADLE_USER_HOME` not set)

`mavenLocalRepoPath`: `$M2_HOME/repository`(or `$USER_HOME/.m2/repository` if `$M2_HOME` not set) 

`exclude`: `gradle/gradle/.+`(modify this regex pattern if you have more files to keep in gradle cache dir)

`dryRun`: `true`

## notice
remember to set `pluginManagement` in `settings.gradle`(or `settings.gradle.kts`) to use local maven repo(or it will download plugin dependencies into gradle cache dir after you moving them into maven local repo dir)

example:
```
pluginManagement {
    repositories {
        mavenLocal()
        // add this if you want to accelerate plugin downloading in china
        maven {
            url 'https://maven.aliyun.com/repository/gradle-plugin'
        }
        gradlePluginPortal()
    }
}
rootProject.name = 'foo.bar'
```
