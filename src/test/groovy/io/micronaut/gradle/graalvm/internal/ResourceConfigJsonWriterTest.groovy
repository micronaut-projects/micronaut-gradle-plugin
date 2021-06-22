package io.micronaut.gradle.graalvm.internal


import spock.lang.Specification

class ResourceConfigJsonWriterTest extends Specification {
    private String actual

    def "can generates resource file with a single value"() {
        when:
        generate 'hello'

        then:
        hasOutput """{
   "resources": [
        { "resource": "hello" }
   ]
}"""
    }

    def "escapes double quotes"() {
        when:
        generate 'value "under quotes" is supported'

        then:
        hasOutput '''{
   "resources": [
        { "resource": "value \\"under quotes\\" is supported" }
   ]
}'''
    }

    def "escapes backslash"() {
        when:
        generate 'value with \\ backslash'

        then:
        hasOutput """{
   "resources": [
        { "resource": "value with \\\\ backslash" }
   ]
}"""
    }

    def "can generate multiple entries in map"() {
        when:
        generateListOfKeyValuePairs([
                [one: 'first'],
                [two: 'second', more: 'extra']
        ])

        then:
        hasOutput """{
   "resources": [
        { "one": "first" }, 
        { "two": "second", "more": "extra" }
   ]
}"""
    }

    def "supports same keys in maps"() {
        when:
        generateListOfKeyValuePairs([
                [pattern: '*'],
                [pattern: 'hello'],
                [pattern: '\\Qworld\\E']
        ])

        then:
        hasOutput """{
   "resources": [
        { "pattern": "*" }, 
        { "pattern": "hello" }, 
        { "pattern": "\\\\Qworld\\\\E" }
   ]
}"""
    }

    private void generate(String value) {
        generateKeyValue('resource', value)
    }

    private void generateKeyValue(String key, String value) {
        generateKeyValuePairs([(key): (value)])
    }

    private void generateKeyValuePairs(Map<String, String> pairs) {
        generateListOfKeyValuePairs([pairs])
    }

    private void generateListOfKeyValuePairs(List<Map<String, String>> list) {
        def baos = new ByteArrayOutputStream()
        ResourceConfigJsonWriter.generateJsonFile(
                list, baos
        )
        actual = normalizeForComparison(baos.toString("UTF-8"))
    }

    private static String normalizeForComparison(String src) {
        src.replaceAll('\r', '').trim()
    }

    private void hasOutput(String expected) {
        def normalizedExpected = normalizeForComparison(expected)
        assert actual == normalizedExpected
    }
}
