package io.micronaut.internal.build.docs

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject
import java.nio.file.Path
import java.util.regex.Pattern

@CompileStatic
@CacheableTask
abstract class Themer extends DefaultTask {
    private static final Pattern TITLE_PATTERN = ~"<title>(.+)</title>"

    private static final String TOC_START = '''\
<div id="toc" class="toc2">
<div id="toctitle">Table of Contents</div>
'''

    private static final String CONTENT_START ='''\
</div>
</div>
<div id="content">
'''
    private static final String BODY_START = '<div class="sectionbody">'

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    abstract RegularFileProperty getTemplate()

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract DirectoryProperty getAsciidocHtml()

    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory()

    @Inject
    abstract FileOperations getFileOperations();

    @TaskAction
    void process() {
        String template = getTemplate().get().asFile.getText('utf-8')
        Path asciidocRoot = asciidocHtml.get().asFile.toPath()
        File outputDirRoot = outputDirectory.get().asFile
        asciidocHtml.asFileTree.each {file ->
            String relativePath = asciidocRoot.relativize(file.toPath())
            fileOperations.mkdir(new File(outputDirRoot, relativePath).parentFile)
            if (file.getName().endsWith(".html")) {
                String text = new String(template)
                try {
                    String html = file.getText('utf-8')
                    def matcher = TITLE_PATTERN.matcher(html)
                    String title = "Title"
                    if (matcher.find()) {
                        title = matcher.group(1)
                    }
                    String toc = html.indexOf(CONTENT_START) != -1 ? html.substring(html.indexOf(TOC_START) + TOC_START.length(),
                            html.indexOf(CONTENT_START)) : ''
                    String content = html.indexOf(BODY_START) != -1 ? html.substring(html.indexOf(BODY_START) + BODY_START.length(),
                            html.indexOf('''\
</div>
</div>
</div>
<script src="highlight/highlight.min.js"></script>
<script>
if (!hljs.initHighlighting.called) {
  hljs.initHighlighting.called = true
  ;[].slice.call(document.querySelectorAll('pre.highlight > code[data-lang]')).forEach(function (el) { hljs.highlightBlock(el) })
}
</script>
</body>
''')) : ''
                    content = content != '' ? ('''\
<div class="sect1">
<div class="sectionbody">
''' + content) : ''
                    text = text.replace("@title@", title)
                    text = text.replace("@toctitle@", 'Table of Contents')
                    text = text.replace("@bodyclass@", 'guide')
                    text = text.replace("@toccontent@", toc)
                    text = text.replace("@content@", content)
                } catch (Throwable ex) {
                    throw new GradleException("Error while processing template", ex)
                }
                new File(outputDirRoot, relativePath).setText(text, 'utf-8')
            } else {
                new File(outputDirRoot, relativePath).bytes = file.bytes
            }
        }
    }

}
