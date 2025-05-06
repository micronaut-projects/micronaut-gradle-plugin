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
            HELLO
            !
        """

        when:
        editor.before("!") {
            it.insert("WORLD")
        }

        then:
        hasUpdatedText """
            HELLO
            WORLD
            !
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
