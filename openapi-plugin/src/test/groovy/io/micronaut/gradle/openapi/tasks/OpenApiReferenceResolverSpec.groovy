package io.micronaut.gradle.openapi.tasks

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class OpenApiReferenceResolverSpec extends Specification {

    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    def "recursively resolves local refs and ignores remote or document-only refs"() {
        given:
        def root = temporaryFolder.newFile("openapi.yml")
        def schemaDir = new File(temporaryFolder.root, "schemas")
        def nestedDir = new File(schemaDir, "nested")
        nestedDir.mkdirs()
        def request = new File(schemaDir, "request.json")
        def response = new File(schemaDir, "response.json")
        def common = new File(nestedDir, "common.json")
        def remote = new File(schemaDir, "remote.json")

        root.text = """
            openapi: "3.0.0"
            paths:
              /foo:
                post:
                  requestBody:
                    content:
                      application/json:
                        schema:
                          \$ref: './schemas/request.json#/\$defs/Req'
                  responses:
                    "200":
                      description: ok
                      content:
                        application/json:
                          schema:
                            \$ref: '${response.toURI()}#/\$defs/Res'
        """.stripIndent()
        request.text = '''
            {
              "$defs": {
                "Req": {
                  "$ref": "./nested/common.json#/$defs/Common"
                }
              }
            }
        '''.stripIndent()
        response.text = '''
            {
              "$defs": {
                "Res": {
                  "$ref": "#/$defs/InternalOnly"
                }
              }
            }
        '''.stripIndent()
        common.text = '''
            {
              "$defs": {
                "Common": {
                  "$ref": "https://example.com/remote.json#/$defs/Remote"
                }
              }
            }
        '''.stripIndent()
        remote.text = '{}'

        when:
        def referencedFiles = OpenApiReferenceResolver.referencedFiles(root)*.canonicalFile as Set

        then:
        referencedFiles == [request.canonicalFile, response.canonicalFile, common.canonicalFile] as Set
        !referencedFiles.contains(remote.canonicalFile)
    }

    def "ignores ref-like text in comments and scalar values"() {
        given:
        def root = temporaryFolder.newFile("openapi.yml")
        def tracked = new File(temporaryFolder.root, "tracked.json")
        def ignored = new File(temporaryFolder.root, "ignored.json")

        root.text = """
            openapi: "3.0.0"
            # \$ref: './ignored.json'
            paths:
              /foo:
                get:
                  responses:
                    "200":
                      description: |
                        Example payload mentioning \$ref: './ignored.json'
                      content:
                        application/json:
                          schema:
                            \$ref: './tracked.json#/\$defs/Tracked'
        """.stripIndent()
        tracked.text = '''
            {
              "$defs": {
                "Tracked": {
                  "type": "string"
                }
              }
            }
        '''.stripIndent()
        ignored.text = '{}'

        when:
        def referencedFiles = OpenApiReferenceResolver.referencedFiles(root)*.canonicalFile as Set

        then:
        referencedFiles == [tracked.canonicalFile] as Set
        !referencedFiles.contains(ignored.canonicalFile)
    }

    def "handles self-referential yaml aliases without infinite recursion"() {
        given:
        def root = temporaryFolder.newFile("openapi.yml")
        def tracked = new File(temporaryFolder.root, "tracked.json")

        root.text = """
            openapi: "3.0.0"
            components:
              schemas:
                Loop: &loop
                  \$ref: './tracked.json#/\$defs/Tracked'
                  self: *loop
            paths: {}
        """.stripIndent()
        tracked.text = '''
            {
              "$defs": {
                "Tracked": {
                  "type": "string"
                }
              }
            }
        '''.stripIndent()

        when:
        def referencedFiles = OpenApiReferenceResolver.referencedFiles(root)*.canonicalFile as Set

        then:
        referencedFiles == [tracked.canonicalFile] as Set
    }

    def "uses safe yaml loading for tagged values"() {
        given:
        def root = temporaryFolder.newFile("openapi.yml")
        def tracked = new File(temporaryFolder.root, "tracked.json")

        root.text = """
            openapi: "3.0.0"
            paths:
              /foo:
                get:
                  responses:
                    "200":
                      content:
                        application/json:
                          schema:
                            \$ref: !!java.net.URL '${tracked.toURI()}#/\$defs/Tracked'
        """.stripIndent()
        tracked.text = '{}'

        when:
        def referencedFiles = OpenApiReferenceResolver.referencedFiles(root)

        then:
        referencedFiles.empty
    }

    def "recognizes windows absolute paths as local filesystem refs"() {
        expect:
        OpenApiReferenceResolver.isWindowsAbsolutePath('C:/schemas/request.json')
        OpenApiReferenceResolver.isWindowsAbsolutePath('C:\\schemas\\request.json')
        !OpenApiReferenceResolver.isWindowsAbsolutePath('./schemas/request.json')
        !OpenApiReferenceResolver.isWindowsAbsolutePath('file:///schemas/request.json')
    }
}
