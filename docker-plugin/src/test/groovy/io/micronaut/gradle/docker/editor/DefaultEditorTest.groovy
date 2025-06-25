package io.micronaut.gradle.docker.editor

import org.gradle.api.model.ObjectFactory
import spock.lang.Specification
import spock.lang.Subject

class DefaultEditorTest extends Specification {
    @Subject
    private DefaultEditor editor

    void "inserts at beginning by default"() {
        given:
        withText """
            FROM openjdk:8-jdk-alpine
            WORKDIR /app
            COPY build/libs/*.jar app.jar
            ENTRYPOINT ["java","-jar","app.jar"]
        """

        when:
        editor.insert("# Some comment")

        then:
        hasUpdatedText """
            # Some comment
            FROM openjdk:8-jdk-alpine
            WORKDIR /app
            COPY build/libs/*.jar app.jar
            ENTRYPOINT ["java","-jar","app.jar"]
        """
    }

    void "can insert before a specific line"() {
        withText """
              FROM public.ecr.aws/amazonlinux/amazonlinux:2023-minimal AS graalvm
              ENV LANG=en_US.UTF-8
              RUN dnf update -y && dnf install -y gcc glibc-devel zlib-devel libstdc++-static tar && dnf clean all && rm -rf /var/cache/dnf
              RUN curl -4 -L https://download.oracle.com/graalvm/21/latest/graalvm-jdk-21_linux-x64_bin.tar.gz -o /tmp/graalvm-jdk-21_linux-x64_bin.tar.gz
              RUN tar -zxf /tmp/graalvm-jdk-21_linux-x64_bin.tar.gz -C /tmp && ls -d /tmp/graalvm-jdk-21* | grep -v "tar.gz" | xargs -I_ mv _ /usr/lib/graalvm
              RUN rm -rf /tmp/*
              CMD ["/usr/lib/graalvm/bin/native-image"]
              ENV PATH=/usr/lib/graalvm/bin:\${PATH}
              FROM graalvm AS builder
              RUN dnf update -y && dnf install -y zip && dnf clean all
              WORKDIR /home/app
              COPY --link layers/libs /home/app/libs
              COPY --link layers/app /home/app/
              COPY --link layers/resources /home/app/resources
              RUN mkdir /home/app/config-dirs
              RUN mkdir -p /home/app/config-dirs/generateResourcesConfigFile
              RUN mkdir -p /home/app/config-dirs/com.fasterxml.jackson.core/jackson-databind/2.15.2
              RUN mkdir -p /home/app/config-dirs/io.netty/netty-common/4.1.115.Final
              RUN mkdir -p /home/app/config-dirs/io.netty/netty-transport/4.1.115.Final
              RUN mkdir -p /home/app/config-dirs/org.apache.commons/commons-pool2/2.11.1
              RUN mkdir -p /home/app/config-dirs/org.apache.httpcomponents/httpclient/4.5.14
              RUN mkdir -p /home/app/config-dirs/commons-logging/commons-logging/1.2
              RUN mkdir -p /home/app/config-dirs/ch.qos.logback/logback-classic/1.4.9
              COPY --link config-dirs/generateResourcesConfigFile /home/app/config-dirs/generateResourcesConfigFile
              COPY --link config-dirs/com.fasterxml.jackson.core/jackson-databind/2.15.2 /home/app/config-dirs/com.fasterxml.jackson.core/jackson-databind/2.15.2
              COPY --link config-dirs/io.netty/netty-common/4.1.115.Final /home/app/config-dirs/io.netty/netty-common/4.1.115.Final
              COPY --link config-dirs/io.netty/netty-transport/4.1.115.Final /home/app/config-dirs/io.netty/netty-transport/4.1.115.Final
              COPY --link config-dirs/org.apache.commons/commons-pool2/2.11.1 /home/app/config-dirs/org.apache.commons/commons-pool2/2.11.1
              COPY --link config-dirs/org.apache.httpcomponents/httpclient/4.5.14 /home/app/config-dirs/org.apache.httpcomponents/httpclient/4.5.14
              COPY --link config-dirs/commons-logging/commons-logging/1.2 /home/app/config-dirs/commons-logging/commons-logging/1.2
              COPY --link config-dirs/ch.qos.logback/logback-classic/1.4.9 /home/app/config-dirs/ch.qos.logback/logback-classic/1.4.9
              RUN native-image --exclude-config .*/libs/netty-buffer-4.1.119.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-common-4.1.119.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-transport-4.1.119.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-codec-http-4.1.119.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-codec-http2-4.1.119.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-handler-4.1.119.Final.jar ^/META-INF/native-image/.* -cp /home/app/libs/*.jar:/home/app/resources:/home/app/application.jar --no-fallback -o application -H:ConfigurationFileDirectories=/home/app/config-dirs/generateResourcesConfigFile,/home/app/config-dirs/com.fasterxml.jackson.core/jackson-databind/2.15.2,/home/app/config-dirs/io.netty/netty-common/4.1.115.Final,/home/app/config-dirs/io.netty/netty-buffer/4.1.80.Final,/home/app/config-dirs/io.netty/netty-transport/4.1.115.Final,/home/app/config-dirs/io.netty/netty-codec-http/4.1.80.Final,/home/app/config-dirs/io.netty/netty-handler/4.1.80.Final,/home/app/config-dirs/io.netty/netty-codec-http2/4.1.80.Final,/home/app/config-dirs/org.apache.commons/commons-pool2/2.11.1,/home/app/config-dirs/org.apache.httpcomponents/httpclient/4.5.14,/home/app/config-dirs/commons-logging/commons-logging/1.2,/home/app/config-dirs/ch.qos.logback/logback-classic/1.4.9 io.micronaut.function.aws.runtime.MicronautLambdaRuntime
              FROM public.ecr.aws/amazonlinux/amazonlinux:2023-minimal
              WORKDIR /function
              RUN dnf install -y zip
              COPY --link --from=builder /home/app/application /function/func
              RUN echo "#!/bin/sh" >> bootstrap && echo "set -euo pipefail" >> bootstrap && echo "./func -XX:MaximumHeapSizePercent=80 -Dio.netty.allocator.numDirectArenas=0 -Dio.netty.noPreferDirect=true -Djava.library.path=\$(pwd)" >> bootstrap
              RUN chmod 777 bootstrap
              RUN chmod 777 func
              RUN zip -j function.zip bootstrap func
              ENTRYPOINT ["/function/func"]
        """

        when:
        editor.before("FROM public.ecr.aws/amazonlinux/amazonlinux:2023-minimal") {
            it.insert("HELLO WORLD")
        }

        then:
        hasUpdatedText """
                FROM public.ecr.aws/amazonlinux/amazonlinux:2023-minimal AS graalvm
                ENV LANG=en_US.UTF-8
                RUN dnf update -y && dnf install -y gcc glibc-devel zlib-devel libstdc++-static tar && dnf clean all && rm -rf /var/cache/dnf
                RUN curl -4 -L https://download.oracle.com/graalvm/21/latest/graalvm-jdk-21_linux-x64_bin.tar.gz -o /tmp/graalvm-jdk-21_linux-x64_bin.tar.gz
                RUN tar -zxf /tmp/graalvm-jdk-21_linux-x64_bin.tar.gz -C /tmp && ls -d /tmp/graalvm-jdk-21* | grep -v "tar.gz" | xargs -I_ mv _ /usr/lib/graalvm
                RUN rm -rf /tmp/*
                CMD ["/usr/lib/graalvm/bin/native-image"]
                ENV PATH=/usr/lib/graalvm/bin:\${PATH}
                FROM graalvm AS builder
                RUN dnf update -y && dnf install -y zip && dnf clean all
                WORKDIR /home/app
                COPY --link layers/libs /home/app/libs
                COPY --link layers/app /home/app/
                COPY --link layers/resources /home/app/resources
                RUN mkdir /home/app/config-dirs
                RUN mkdir -p /home/app/config-dirs/generateResourcesConfigFile
                RUN mkdir -p /home/app/config-dirs/com.fasterxml.jackson.core/jackson-databind/2.15.2
                RUN mkdir -p /home/app/config-dirs/io.netty/netty-common/4.1.115.Final
                RUN mkdir -p /home/app/config-dirs/io.netty/netty-transport/4.1.115.Final
                RUN mkdir -p /home/app/config-dirs/org.apache.commons/commons-pool2/2.11.1
                RUN mkdir -p /home/app/config-dirs/org.apache.httpcomponents/httpclient/4.5.14
                RUN mkdir -p /home/app/config-dirs/commons-logging/commons-logging/1.2
                RUN mkdir -p /home/app/config-dirs/ch.qos.logback/logback-classic/1.4.9
                COPY --link config-dirs/generateResourcesConfigFile /home/app/config-dirs/generateResourcesConfigFile
                COPY --link config-dirs/com.fasterxml.jackson.core/jackson-databind/2.15.2 /home/app/config-dirs/com.fasterxml.jackson.core/jackson-databind/2.15.2
                COPY --link config-dirs/io.netty/netty-common/4.1.115.Final /home/app/config-dirs/io.netty/netty-common/4.1.115.Final
                COPY --link config-dirs/io.netty/netty-transport/4.1.115.Final /home/app/config-dirs/io.netty/netty-transport/4.1.115.Final
                COPY --link config-dirs/org.apache.commons/commons-pool2/2.11.1 /home/app/config-dirs/org.apache.commons/commons-pool2/2.11.1
                COPY --link config-dirs/org.apache.httpcomponents/httpclient/4.5.14 /home/app/config-dirs/org.apache.httpcomponents/httpclient/4.5.14
                COPY --link config-dirs/commons-logging/commons-logging/1.2 /home/app/config-dirs/commons-logging/commons-logging/1.2
                COPY --link config-dirs/ch.qos.logback/logback-classic/1.4.9 /home/app/config-dirs/ch.qos.logback/logback-classic/1.4.9
                RUN native-image --exclude-config .*/libs/netty-buffer-4.1.119.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-common-4.1.119.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-transport-4.1.119.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-codec-http-4.1.119.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-codec-http2-4.1.119.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-handler-4.1.119.Final.jar ^/META-INF/native-image/.* -cp /home/app/libs/*.jar:/home/app/resources:/home/app/application.jar --no-fallback -o application -H:ConfigurationFileDirectories=/home/app/config-dirs/generateResourcesConfigFile,/home/app/config-dirs/com.fasterxml.jackson.core/jackson-databind/2.15.2,/home/app/config-dirs/io.netty/netty-common/4.1.115.Final,/home/app/config-dirs/io.netty/netty-buffer/4.1.80.Final,/home/app/config-dirs/io.netty/netty-transport/4.1.115.Final,/home/app/config-dirs/io.netty/netty-codec-http/4.1.80.Final,/home/app/config-dirs/io.netty/netty-handler/4.1.80.Final,/home/app/config-dirs/io.netty/netty-codec-http2/4.1.80.Final,/home/app/config-dirs/org.apache.commons/commons-pool2/2.11.1,/home/app/config-dirs/org.apache.httpcomponents/httpclient/4.5.14,/home/app/config-dirs/commons-logging/commons-logging/1.2,/home/app/config-dirs/ch.qos.logback/logback-classic/1.4.9 io.micronaut.function.aws.runtime.MicronautLambdaRuntime
                HELLO WORLD
                FROM public.ecr.aws/amazonlinux/amazonlinux:2023-minimal
                WORKDIR /function
                RUN dnf install -y zip
                COPY --link --from=builder /home/app/application /function/func
                RUN echo "#!/bin/sh" >> bootstrap && echo "set -euo pipefail" >> bootstrap && echo "./func -XX:MaximumHeapSizePercent=80 -Dio.netty.allocator.numDirectArenas=0 -Dio.netty.noPreferDirect=true -Djava.library.path=\$(pwd)" >> bootstrap
                RUN chmod 777 bootstrap
                RUN chmod 777 func
                RUN zip -j function.zip bootstrap func
                ENTRYPOINT ["/function/func"]
        """
    }

    void "can insert after a specific line and before another specific line"() {
        withText """
            HELLO
            !
        """

        when:
        editor.after("HELLO") {
            it.before("!") {
                it.insert("WORLD")
            }
        }

        then:
        hasUpdatedText """
            HELLO
            WORLD
            !
        """
    }

    void "can insert after a specific line"() {
        withText """
            HELLO
            !
        """

        when:
        editor.after("HELLO") {
            it.insert("WORLD")
        }

        then:
        hasUpdatedText """
            HELLO
            WORLD
            !
        """
    }

    void "can replace a line after a marker"() {
        withText """
            EXIT
            HELLO
            WORLD
            EXIT
            !
        """

        when:
        editor.after("HELLO") {
            it.replace("EXIT", "BYE")
        }

        then:
        hasUpdatedText """
            EXIT
            HELLO
            WORLD
            BYE
            !
        """
    }

    def "reasonable error message when marker can't be found"() {
        when:
        withText """
            HELLO
            WORLD
            !
        """
        editor.after("NOPE") {
            it.replace('HELLO', 'BYE')
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Unable to find line NOPE"
    }

    def "can find more complex patterns"() {
        given:
        withText """
            A
            A
            B
            C
            A
            A
            B   
        """
        when:
        editor.after('C') {
            it.after('A') {
                it.after('A') {
                    it.insert('D')
                }
            }
        }

        then:
        hasUpdatedText """
            A
            A
            B
            C
            A
            A
            D
            B   
        """
    }

    def "can combine multiple inserts"() {
        withText """
            A
            B
        """

        when:
        editor.after('A') {
            it.insert('C')
            it.after('B') {
                it.insert('D')
            }
        }

        then:
        hasUpdatedText """
            A
            C
            B
            D
        """
    }

    def "can replace multiple lines"() {
        withText """
            A
            B
            C
            D
            E
        """

        when:
        editor.after('A') {
            it.before('E') {
                it.replace('0')
            }
        }

        then:
        hasUpdatedText """
            A
            0
            0
            0
            E
        """

    }

    private void withText(String text) {
        def objects = Stub(ObjectFactory) {
            newInstance(_, _) >> {
                it[0].newInstance(it[1])
            }
        }
        editor = new DefaultEditor(objects, Optional.empty(), normalize(text).split("\n") as List<String>, Optional.empty(), Optional.empty())
    }

    private void hasUpdatedText(String expected) {
        String updated = normalize(editor.getLines().join("\n"))
        expected = normalize(expected)
        assert updated == expected
    }

    private static String normalize(String text) {
        List<String> lines = text.split("\r?\n") as List<String>
        return lines.findAll()
                .collect { it.stripIndent().trim() }
                .join("\n")
                .trim()
    }

}
